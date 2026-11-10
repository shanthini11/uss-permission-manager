package com.group8.permissionmanagerapp_uss.data.devices

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

/**
 * A new device asking to join (refinements #3/#4). The join code itself is held only by the
 * verification service; Firestore just records when the current code expires.
 */
data class JoinRequest(
    val id: String,
    val name: String,
    val model: String,
    val os: String,
    val requestedAt: Long,
    val status: String,
    val codeExpiresAt: Long?,
) {
    val denied: Boolean get() = status == STATUS_DENIED
    val hasCode: Boolean get() = (codeExpiresAt ?: 0) > System.currentTimeMillis()

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

/** Join codes are valid for 5 minutes (enforced by the verification service). */
object DeviceCode {
    const val TTL_MS = 5 * 60 * 1000L
}
