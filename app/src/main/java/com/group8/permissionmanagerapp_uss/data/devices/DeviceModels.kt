package com.group8.permissionmanagerapp_uss.data.devices

import java.security.MessageDigest
import java.security.SecureRandom

/** A short summary a device shares with the account's other devices (can be turned off). */
data class DeviceSummary(
    val appCount: Int = 0,
    val highRisk: Int = 0,
    val background: Int = 0,
    val critical: Int = 0,
    val topApps: List<TopApp> = emptyList(),
    val updatedAt: Long = 0,
)

data class TopApp(val label: String, val risk: String, val permissions: String)

/** A device connected to the account (refinement #2 "My Devices"). */
data class DeviceInfo(
    val id: String,
    val name: String,
    val model: String,
    val os: String,
    val addedAt: Long,
    val lastActive: Long,
    val shareSummary: Boolean,
    val summary: DeviceSummary?,
    val isThisDevice: Boolean,
)

/** A new device asking to join (refinements #3/#4). */
data class JoinRequest(
    val id: String,
    val name: String,
    val model: String,
    val os: String,
    val requestedAt: Long,
    val status: String,
    val codeExpiresAt: Long?,
    val attempts: Int,
    val hasCode: Boolean,
) {
    val denied: Boolean get() = status == STATUS_DENIED

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DENIED = "denied"
    }
}

/** Where this phone stands with the signed-in account. */
sealed interface DeviceStatus {
    data object Approved : DeviceStatus
    /** Not part of the account yet. [first] means the account has no devices, so it's added automatically. */
    data class NeedsSetup(val first: Boolean, val otherDevices: List<DeviceInfo>) : DeviceStatus
    data class Pending(val request: JoinRequest, val otherDevices: List<DeviceInfo>) : DeviceStatus
    /** This phone was approved before but has since been removed from the account. */
    data object Removed : DeviceStatus
}

/**
 * 6-digit join codes. The code itself is never stored: only a salted, slow PBKDF2 hash, and it
 * expires after 5 minutes with at most 5 attempts.
 */
object DeviceCode {
    const val TTL_MS = 5 * 60 * 1000L
    const val MAX_ATTEMPTS = 5
    private const val ITERATIONS = 20_000

    fun newCode(random: SecureRandom = SecureRandom()): String =
        String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000))

    fun newSalt(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }

    fun hash(code: String, deviceId: String, salt: String): String {
        val spec = javax.crypto.spec.PBEKeySpec("$code:$deviceId".toCharArray(), salt.toByteArray(), ITERATIONS, 256)
        val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Constant-time comparison of the entered code's hash with the stored one. */
    fun matches(code: String, deviceId: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(code, deviceId, salt).toByteArray(), expectedHash.toByteArray())
}
