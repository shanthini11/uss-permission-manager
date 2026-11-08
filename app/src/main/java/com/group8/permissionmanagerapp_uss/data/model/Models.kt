package com.group8.permissionmanagerapp_uss.data.model

/**
 * How sensitive a permission category is (SR2). Shown as the CRITICAL / SENSITIVE tags in the lo-fi.
 */
enum class Sensitivity(val label: String) {
    CRITICAL("Critical"),
    SENSITIVE("Sensitive"),
    LOW("Low"),
}

/**
 * The access an app currently has for one permission category, in user-facing terms (R3).
 *
 * Android does not expose "Ask every time" for other apps; a one-time grant shows as [NOT_ALLOWED]
 * once the app is closed, which is also what the system Settings app shows.
 */
enum class AccessLevel(val label: String, val isGranted: Boolean) {
    /** Can be used even when the app is closed (e.g. background location). */
    ALWAYS("Always allowed", true),

    /** Only while the app is open on screen. */
    WHILE_IN_USE("Only while using the app", true),

    /** Granted, with no foreground/background distinction (e.g. contacts, SMS). */
    ALLOWED("Allowed", true),

    /** Granted with reduced scope: approximate location or selected photos only (SR7). */
    LIMITED("Limited access", true),

    NOT_ALLOWED("Not allowed", false),
}

/** One permission category that a specific app has requested, and what it currently holds. */
data class AppPermission(
    val category: PermissionCategory,
    val access: AccessLevel,
    /** Extra detail for granular access, e.g. "Approximate location" or "Selected photos only". */
    val detail: String? = null,
    /** Raw Android permission names, shown in the "technical details" disclosure only. */
    val androidPermissions: List<String> = emptyList(),
) {
    val isBackground: Boolean get() = access == AccessLevel.ALWAYS
}

enum class RiskLevel(val label: String) {
    HIGH("High risk"),
    MEDIUM("Medium risk"),
    LOW("Low risk"),
}

/** An installed app as the Permission Manager sees it. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val permissions: List<AppPermission>,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    /** Plain-language reasons behind [riskLevel], most serious first (SR5). */
    val riskReasons: List<String>,
    /** Epoch millis of the last install/update, used for "recently installed" hints. */
    val lastUpdatedAt: Long,
) {
    val grantedPermissions: List<AppPermission> get() = permissions.filter { it.access.isGranted }
    val hasBackgroundAccess: Boolean get() = permissions.any { it.isBackground }

    fun accessFor(category: PermissionCategory): AppPermission? =
        permissions.firstOrNull { it.category == category }

    companion object {
        /**
         * List order: apps the user installed first, riskiest first; pre-installed apps after them.
         * Pre-installed apps (Phone, Messages...) need broad access to work, so putting them first
         * would bury the apps the user actually chose to install.
         */
        val RiskOrder: Comparator<InstalledApp> =
            compareBy<InstalledApp> { it.isSystemApp }
                .thenByDescending { it.riskScore }
                .thenBy { it.label.lowercase() }
    }
}
