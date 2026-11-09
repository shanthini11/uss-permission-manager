package com.group8.permissionmanagerapp_uss.data.history

import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import kotlinx.serialization.Serializable

/** How a permission change was noticed. */
@Serializable
enum class EventSource(val label: String) {
    /** The user changed it through our permission sheet (and we verified it). */
    IN_APP("Changed using ${com.group8.permissionmanagerapp_uss.AppInfo.NAME}"),

    /** We noticed it on a later check: changed in Android Settings, or the app asked and you answered. */
    DETECTED("Detected: changed outside this app"),

    /** Weekly "time to review" reminder (not a permission change). */
    REVIEW_REMINDER("Weekly review reminder"),
}

/**
 * One entry in Permission History. Stored encrypted on this device only.
 * Only app names and permission categories are recorded — no personal content.
 */
@Serializable
data class PermissionEvent(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val category: PermissionCategory?,
    /** Access before the change; null if unknown (e.g. first time we saw the app). */
    val from: AccessLevel?,
    val to: AccessLevel?,
    val source: EventSource,
    val isDemo: Boolean,
    /** Dismissed from Notifications. History keeps it regardless. */
    val dismissed: Boolean = false,
) {
    val isReminder: Boolean get() = source == EventSource.REVIEW_REMINDER
    val isGrant: Boolean get() = to?.isGranted == true
}

/** Last-known permission state, used to detect changes between checks. */
@Serializable
data class PermissionSnapshot(
    /** packageName -> (category -> access) */
    val apps: Map<String, Map<PermissionCategory, AccessLevel>> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val takenAt: Long = 0,
)

@Serializable
internal data class HistoryFile(
    val events: List<PermissionEvent> = emptyList(),
    val snapshot: PermissionSnapshot? = null,
    val demoSeeded: Boolean = false,
)
