package com.group8.permissionmanagerapp_uss.data.devices

import android.os.Build
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.SetOptions
import com.group8.permissionmanagerapp_uss.data.auth.AuthRepository
import com.group8.permissionmanagerapp_uss.data.auth.OtpService
import com.group8.permissionmanagerapp_uss.data.auth.await
import com.group8.permissionmanagerapp_uss.data.model.RiskLevel
import com.group8.permissionmanagerapp_uss.data.model.Sensitivity
import com.group8.permissionmanagerapp_uss.data.repository.PermissionRepository
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

class DeviceException(message: String) : Exception(message)

/**
 * Multi-device support on Firestore (free Spark plan). Layout, all under the signed-in user:
 *   users/{uid}                     review preferences, so a new device can import them
 *   users/{uid}/devices/{id}        approved devices + their optional permission summary
 *   users/{uid}/joinRequests/{id}   devices waiting for approval
 *
 * Security: Firestore rules (backend/firestore.rules) require a server-verified 2FA session for
 * every read/write and forbid clients from creating devices. Adding a device, issuing join codes
 * and checking them all happen in the verification service (backend/otp-service/Code.gs).
 */
class DeviceRepository(
    private val auth: AuthRepository,
    private val identity: DeviceIdentity,
    private val backend: OtpService,
    private val devicePermissions: PermissionRepository,
    private val preferences: UserPreferencesRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // In-memory cache only: device summaries are never written to Firestore's on-disk cache.
    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    private fun uid(): String = auth.currentUser?.uid ?: throw DeviceException("You're signed out. Please sign in again.")
    private fun userDoc() = db.collection("users").document(uid())
    private fun devices() = userDoc().collection("devices")
    private fun requests() = userDoc().collection("joinRequests")

    suspend fun thisDeviceId(): String = identity.deviceId()

    /** Works out whether this phone is approved, needs setup, is waiting, or was removed. */
    suspend fun checkStatus(): DeviceStatus = guard {
        val uid = uid()
        val id = identity.deviceId()
        val all = devices().get().await().documents.mapNotNull { toDevice(it, id) }
        val mine = all.firstOrNull { it.isThisDevice }
        val others = all.filterNot { it.isThisDevice }
        when {
            mine != null -> {
                identity.setApprovedFor(uid)
                DeviceStatus.Approved
            }
            identity.isApprovedFor(uid) -> {
                identity.setApprovedFor(null)
                DeviceStatus.Removed
            }
            else -> {
                val request = requests().document(id).get().await().takeIf { it.exists() }?.let(::toRequest)
                if (request != null) DeviceStatus.Pending(request, others)
                else DeviceStatus.NeedsSetup(first = all.isEmpty(), otherDevices = others)
            }
        }
    }

    /** First device on the account. The server checks the account really has no devices. */
    suspend fun registerFirstDevice() = guard {
        val id = identity.deviceId()
        serverAction("registerFirstDevice", mapOf("deviceId" to id, "name" to defaultName(), "model" to model(), "os" to osVersion()))
        identity.setApprovedFor(uid())
    }

    /** Refinement #3 "Add New Device": ask the account's other devices for approval. */
    suspend fun requestToJoin(name: String, importPreferences: Boolean) = guard {
        val id = identity.deviceId()
        if (importPreferences) importAccountPreferences()
        requests().document(id).set(
            mapOf(
                "name" to cleanName(name),
                "model" to model(),
                "os" to osVersion(),
                "requestedAt" to now(),
                "status" to JoinRequest.STATUS_PENDING,
            )
        ).await()
    }

    /** This device's own join request, live (e.g. to notice it was denied or a code was issued). */
    fun observeOwnRequest(deviceId: String): Flow<JoinRequest?> = callbackFlow {
        val reg = requests().document(deviceId).addSnapshotListener { snap, _ ->
            trySend(snap?.takeIf { it.exists() }?.let(::toRequest))
        }
        awaitClose { reg.remove() }
    }

    /** Refinement #4 "Verify New Device": the server checks the code and adds this device. */
    suspend fun verifyJoinCode(code: String): VerifyResult = guard {
        val r = backend.device("verifyDeviceCode", mapOf("deviceId" to identity.deviceId(), "code" to code))
        if (r.ok) {
            identity.setApprovedFor(uid())
            VerifyResult.Ok
        } else VerifyResult.Error(r.message ?: "That code isn't right.")
    }

    /** Fallback when no other device is at hand: the server checks a fresh email code and adds this device. */
    suspend fun approveByEmailCode(code: String): VerifyResult = guard {
        val r = backend.device("approveDeviceByEmail", mapOf("deviceId" to identity.deviceId(), "code" to code))
        if (r.ok) {
            identity.setApprovedFor(uid())
            VerifyResult.Ok
        } else VerifyResult.Error(r.message ?: "That code isn't right.")
    }

    /** Error screen "Request a new code": back to pending so an approved device issues a fresh code. */
    suspend fun resetRequest() = guard {
        requests().document(identity.deviceId()).update(
            mapOf("status" to JoinRequest.STATUS_PENDING, "requestedAt" to now(), "codeExpiresAt" to FieldValue.delete())
        ).await()
    }

    suspend fun cancelRequest() = guard {
        requests().document(identity.deviceId()).delete().await()
    }

    // ---- On an approved device ----

    /** Join requests from other devices, live. */
    fun observeIncomingRequests(): Flow<List<JoinRequest>> = callbackFlow {
        val myId = identity.deviceId()
        val reg = requests().whereEqualTo("status", JoinRequest.STATUS_PENDING).addSnapshotListener { snap, _ ->
            trySend(snap?.documents.orEmpty().mapNotNull(::toRequest).filter { it.id != myId })
        }
        awaitClose { reg.remove() }
    }

    /**
     * Asks the server for a join code for [requestId]; the server checks this phone is an approved
     * device. Returns the code to display, or null if another device is already showing one.
     */
    suspend fun issueCode(requestId: String): String? = guard {
        val r = backend.device("issueDeviceCode", mapOf("approverDeviceId" to identity.deviceId(), "deviceId" to requestId))
        when {
            r.ok -> r.code
            r.message != null && "already showing" in r.message -> null
            else -> throw DeviceException(r.message ?: "Couldn't create a code.")
        }
    }

    suspend fun denyRequest(requestId: String) = guard {
        requests().document(requestId).update(mapOf("status" to JoinRequest.STATUS_DENIED, "codeExpiresAt" to FieldValue.delete())).await()
    }

    fun observeDevices(): Flow<List<DeviceInfo>> = callbackFlow {
        val myId = identity.deviceId()
        val reg = devices().addSnapshotListener { snap, _ ->
            trySend(
                snap?.documents.orEmpty().mapNotNull { toDevice(it, myId) }
                    .sortedWith(compareByDescending<DeviceInfo> { it.isThisDevice }.thenByDescending { it.lastActive })
            )
        }
        awaitClose { reg.remove() }
    }

    /** Refinements #7–#9: remove other devices. This device is removed by logging out instead. */
    suspend fun removeDevices(ids: List<String>) = guard {
        val myId = identity.deviceId()
        val batch = db.batch()
        ids.filter { it != myId }.forEach { batch.delete(devices().document(it)) }
        batch.commit().await()
    }

    suspend fun setShareSummary(enabled: Boolean) = guard {
        val ref = devices().document(identity.deviceId())
        if (enabled) {
            ref.update(mapOf("shareSummary" to true, "lastActive" to now())).await()
            syncThisDevice()
        } else {
            ref.update(mapOf("shareSummary" to false, "summary" to FieldValue.delete(), "lastActive" to now())).await()
        }
    }

    /** Updates "last active", the optional summary and the account's review preferences. */
    suspend fun syncThisDevice() = guard {
        val ref = devices().document(identity.deviceId())
        val snap = ref.get().await()
        if (!snap.exists()) return@guard
        val share = snap.getBoolean("shareSummary") ?: true
        val update = mutableMapOf<String, Any>("lastActive" to now(), "os" to osVersion())
        if (share) update["summary"] = buildSummary()
        ref.update(update).await()

        val prefs = preferences.preferences.first()
        userDoc().set(
            mapOf(
                "notificationsEnabled" to prefs.notificationsEnabled,
                "historyEnabled" to prefs.historyEnabled,
                "periodicReview" to prefs.periodicReview,
                "updatedAt" to now(),
            ),
            SetOptions.merge(),
        ).await()
    }

    // ---- helpers ----

    private suspend fun serverAction(action: String, fields: Map<String, String>) {
        val r = backend.device(action, fields)
        if (!r.ok) throw DeviceException(r.message ?: "Something went wrong. Please try again.")
    }

    private suspend fun importAccountPreferences() {
        val snap = userDoc().get().await()
        if (!snap.exists()) return
        preferences.restoreReviewPreferences(
            notifications = snap.getBoolean("notificationsEnabled") ?: true,
            history = snap.getBoolean("historyEnabled") ?: true,
            periodicReview = snap.getBoolean("periodicReview") ?: true,
        )
    }

    private suspend fun buildSummary(): Map<String, Any> {
        val apps = devicePermissions.loadApps(includeSystemApps = false)
        val userApps = apps.filterNot { it.isSystemApp }
        return mapOf(
            "appCount" to apps.size,
            "highRisk" to apps.count { it.riskLevel == RiskLevel.HIGH },
            "background" to apps.count { it.hasBackgroundAccess },
            "critical" to apps.sumOf { a -> a.grantedPermissions.count { it.category.sensitivity == Sensitivity.CRITICAL } },
            "updatedAt" to now(),
            "topApps" to userApps.sortedByDescending { it.riskScore }.take(MAX_TOP_APPS).map { app ->
                mapOf(
                    "label" to app.label.take(60),
                    "risk" to app.riskLevel.label,
                    "permissions" to app.grantedPermissions.joinToString(", ") { it.category.title }.take(200),
                )
            },
        )
    }

    private fun toDevice(doc: DocumentSnapshot, myId: String): DeviceInfo? {
        val name = doc.getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val summary = (doc.get("summary") as? Map<String, Any?>)?.let { s ->
            DeviceSummary(
                appCount = (s["appCount"] as? Number)?.toInt() ?: 0,
                highRisk = (s["highRisk"] as? Number)?.toInt() ?: 0,
                background = (s["background"] as? Number)?.toInt() ?: 0,
                critical = (s["critical"] as? Number)?.toInt() ?: 0,
                updatedAt = (s["updatedAt"] as? Number)?.toLong() ?: 0,
                topApps = (s["topApps"] as? List<*>).orEmpty().mapNotNull { item ->
                    (item as? Map<*, *>)?.let {
                        TopApp(it["label"] as? String ?: return@let null, it["risk"] as? String ?: "", it["permissions"] as? String ?: "")
                    }
                },
            )
        }
        return DeviceInfo(
            id = doc.id,
            name = name,
            model = doc.getString("model").orEmpty(),
            os = doc.getString("os").orEmpty(),
            addedAt = doc.getLong("addedAt") ?: 0,
            lastActive = doc.getLong("lastActive") ?: 0,
            shareSummary = doc.getBoolean("shareSummary") ?: true,
            summary = summary,
            isThisDevice = doc.id == myId,
        )
    }

    private fun toRequest(doc: DocumentSnapshot): JoinRequest? = JoinRequest(
        id = doc.id,
        name = doc.getString("name") ?: return null,
        model = doc.getString("model").orEmpty(),
        os = doc.getString("os").orEmpty(),
        requestedAt = doc.getLong("requestedAt") ?: 0,
        status = doc.getString("status") ?: JoinRequest.STATUS_PENDING,
        codeExpiresAt = doc.getLong("codeExpiresAt"),
    )

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: DeviceException) {
        throw e
    } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
        if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            throw DeviceException("Your session needs to be verified again. Log out and sign in with your email code.")
        }
        throw DeviceException("Couldn't reach your account. Check your internet connection and try again.")
    } catch (_: Exception) {
        throw DeviceException("Couldn't reach your account. Check your internet connection and try again.")
    }

    fun defaultName(): String = model()

    private fun cleanName(name: String) = name.trim().filter { !it.isISOControl() }.take(MAX_NAME).ifBlank { defaultName() }

    private fun model(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return (if (Build.MODEL.startsWith(maker, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}").take(MAX_NAME)
    }

    private fun osVersion() = "Android ${Build.VERSION.RELEASE}".take(30)

    sealed interface VerifyResult {
        data object Ok : VerifyResult
        data class Error(val message: String) : VerifyResult
    }

    companion object {
        const val MAX_NAME = 40
        const val MAX_TOP_APPS = 10
    }
}
