package com.group8.permissionmanagerapp_uss.data.model

/**
 * An option in the permission sheet (lo-fi "Specific Permission" screen).
 *
 * Android only lets the system Settings app change another app's permissions, so each choice
 * carries [systemOption] — the exact wording to tap in Android Settings — and [matches], used to
 * check the real state afterwards and show "Permission Updated" or "Permission Not Updated".
 */
enum class PermissionChoice(
    val label: String,
    val tag: String?,
    val description: String,
    /** The access level this choice results in (applied directly in Demo mode). */
    val target: AccessLevel,
    val systemOption: String,
) {
    ALLOW_ONCE(
        label = "Allow once",
        tag = "High privacy",
        description = "The app must ask again every time it wants access.",
        target = AccessLevel.NOT_ALLOWED,
        systemOption = "Ask every time",
    ),
    WHILE_IN_USE(
        label = "Allow only while using the app",
        tag = "Recommended",
        description = "Only works while the app is open on your screen.",
        target = AccessLevel.WHILE_IN_USE,
        systemOption = "Allow only while using the app",
    ),
    APPROXIMATE(
        label = "Approximate location only",
        tag = "More private",
        description = "The app sees roughly which area you are in, not your exact spot.",
        target = AccessLevel.LIMITED,
        systemOption = "Allow only while using the app, then turn OFF \"Use precise location\"",
    ),
    ALWAYS(
        label = "Always allow",
        tag = "Broader access",
        description = "Works even when the app is closed. Only choose this if a feature truly needs it.",
        target = AccessLevel.ALWAYS,
        systemOption = "Allow all the time",
    ),
    SELECTED_ONLY(
        label = "Selected photos and videos only",
        tag = "Recommended",
        description = "The app only sees the items you pick.",
        target = AccessLevel.LIMITED,
        systemOption = "Allow limited access",
    ),
    ALLOW_ALL(
        label = "Allow all",
        tag = "Broader access",
        description = "The app can read every photo, video and file.",
        target = AccessLevel.ALLOWED,
        systemOption = "Always allow all",
    ),
    ALLOW(
        label = "Allow",
        tag = null,
        description = "The app can use this whenever it needs to.",
        target = AccessLevel.ALLOWED,
        systemOption = "Allow",
    ),
    DENY(
        label = "Don't allow",
        tag = "Most private",
        description = "The app can't use this. Features that need it will stop working.",
        target = AccessLevel.NOT_ALLOWED,
        systemOption = "Don't allow",
    );

    /** True if the app's real access after the change is what the user asked for. */
    fun matches(actual: AccessLevel): Boolean = when (this) {
        // A one-time grant is gone once the app closes, so it reads as not allowed.
        ALLOW_ONCE, DENY -> !actual.isGranted
        ALLOW, ALLOW_ALL -> actual.isGranted && actual != AccessLevel.LIMITED
        else -> actual == target
    }

    companion object {
        /** Options offered for a category, in the order shown in the sheet. */
        fun optionsFor(category: PermissionCategory): List<PermissionChoice> = when (category) {
            PermissionCategory.LOCATION -> listOf(ALLOW_ONCE, WHILE_IN_USE, APPROXIMATE, ALWAYS, DENY)
            PermissionCategory.CAMERA, PermissionCategory.MICROPHONE -> listOf(ALLOW_ONCE, WHILE_IN_USE, DENY)
            PermissionCategory.BODY_SENSORS -> listOf(WHILE_IN_USE, ALWAYS, DENY)
            PermissionCategory.FILES_PHOTOS -> listOf(SELECTED_ONLY, ALLOW_ALL, DENY)
            else -> listOf(ALLOW, DENY)
        }

        /** The option that describes the app's current access, so the sheet can pre-select it. */
        fun current(permission: AppPermission): PermissionChoice = when (permission.access) {
            AccessLevel.ALWAYS -> ALWAYS
            AccessLevel.WHILE_IN_USE -> WHILE_IN_USE
            AccessLevel.LIMITED ->
                if (permission.category == PermissionCategory.LOCATION) APPROXIMATE else SELECTED_ONLY
            AccessLevel.ALLOWED ->
                if (permission.category == PermissionCategory.FILES_PHOTOS) ALLOW_ALL else ALLOW
            AccessLevel.NOT_ALLOWED -> DENY
        }

        /** Name of the category as it appears on Android's App permissions screen. */
        fun systemCategoryName(category: PermissionCategory): String = when (category) {
            PermissionCategory.FILES_PHOTOS -> "Photos and videos"
            PermissionCategory.PHONE -> "Phone"
            PermissionCategory.SMS -> "SMS"
            PermissionCategory.BODY_SENSORS -> "Body sensors"
            PermissionCategory.PHYSICAL_ACTIVITY -> "Physical activity"
            PermissionCategory.NEARBY_DEVICES -> "Nearby devices"
            else -> category.title
        }
    }
}
