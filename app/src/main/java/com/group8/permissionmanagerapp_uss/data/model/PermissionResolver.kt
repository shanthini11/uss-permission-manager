package com.group8.permissionmanagerapp_uss.data.model

/**
 * Converts raw Android permission grants into user-facing [AppPermission]s and a risk score.
 *
 * Kept free of Android framework classes so it can be unit-tested on the JVM.
 */
object PermissionResolver {

    private const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    private const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    private const val READ_MEDIA_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

    /** Categories where a foreground grant means "only while using the app". */
    private val foregroundOnlyCategories = setOf(
        PermissionCategory.LOCATION,
        PermissionCategory.CAMERA,
        PermissionCategory.MICROPHONE,
        PermissionCategory.BODY_SENSORS,
    )

    /**
     * @param requested map of every permission the app requested -> whether it is currently granted.
     * @return one [AppPermission] per tracked category the app requested, most sensitive first.
     */
    fun resolve(requested: Map<String, Boolean>): List<AppPermission> {
        val byCategory = requested.entries
            .mapNotNull { (perm, granted) ->
                PermissionCategory.forAndroidPermission(perm)?.let { Triple(it, perm, granted) }
            }
            .groupBy({ it.first }, { it.second to it.third })

        return byCategory.map { (category, perms) ->
            val grants = perms.toMap()
            val (access, detail) = accessFor(category, grants)
            AppPermission(
                category = category,
                access = access,
                detail = detail,
                androidPermissions = perms.map { it.first }.sorted(),
            )
        }.sortedWith(
            compareBy<AppPermission> { it.category.sensitivity.ordinal }
                .thenByDescending { it.access.isGranted }
                .thenBy { it.category.ordinal }
        )
    }

    private fun accessFor(
        category: PermissionCategory,
        grants: Map<String, Boolean>,
    ): Pair<AccessLevel, String?> {
        val bg = category.backgroundPermission
        val foregroundGranted = grants.any { (perm, granted) -> granted && perm != bg }
        val backgroundGranted = bg != null && grants[bg] == true

        if (!foregroundGranted && !backgroundGranted) return AccessLevel.NOT_ALLOWED to null

        return when (category) {
            PermissionCategory.LOCATION -> {
                val precise = grants[FINE_LOCATION] == true
                val approximate = !precise && grants[COARSE_LOCATION] == true
                when {
                    backgroundGranted -> AccessLevel.ALWAYS to
                        if (precise) "Precise location, even when the app is closed"
                        else "Approximate location, even when the app is closed"
                    approximate -> AccessLevel.LIMITED to "Approximate location only, while using the app"
                    else -> AccessLevel.WHILE_IN_USE to "Precise location"
                }
            }

            PermissionCategory.FILES_PHOTOS -> {
                val fullMedia = grants[READ_MEDIA_IMAGES] == true ||
                    grants[READ_MEDIA_VIDEO] == true ||
                    grants[READ_EXTERNAL_STORAGE] == true
                val selectedOnly = !fullMedia && grants[READ_MEDIA_SELECTED] == true
                if (selectedOnly) AccessLevel.LIMITED to "Selected photos and videos only"
                else AccessLevel.ALLOWED to "All photos, videos and files"
            }

            else -> when {
                backgroundGranted -> AccessLevel.ALWAYS to "Even when the app is closed"
                category in foregroundOnlyCategories -> AccessLevel.WHILE_IN_USE to null
                else -> AccessLevel.ALLOWED to null
            }
        }
    }

    /**
     * Risk score: sum of category weights for granted permissions.
     * Background ("always") access doubles the weight; limited access halves it.
     */
    fun riskScore(permissions: List<AppPermission>): Int =
        permissions.sumOf { p ->
            val w = p.category.riskWeight
            when (p.access) {
                AccessLevel.ALWAYS -> w * 2
                AccessLevel.WHILE_IN_USE, AccessLevel.ALLOWED -> w
                AccessLevel.LIMITED -> (w + 1) / 2
                AccessLevel.NOT_ALLOWED -> 0
            }
        }

    fun riskLevel(permissions: List<AppPermission>): RiskLevel {
        val score = riskScore(permissions)
        val criticalInBackground = permissions.any {
            it.isBackground && it.category.sensitivity == Sensitivity.CRITICAL
        }
        return when {
            criticalInBackground || score >= HIGH_THRESHOLD -> RiskLevel.HIGH
            score >= MEDIUM_THRESHOLD -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    /** Builds an [InstalledApp] with risk score, level and reasons computed consistently. */
    fun buildApp(
        packageName: String,
        label: String,
        isSystemApp: Boolean,
        permissions: List<AppPermission>,
        lastUpdatedAt: Long,
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        isSystemApp = isSystemApp,
        permissions = permissions,
        riskScore = riskScore(permissions),
        riskLevel = riskLevel(permissions),
        riskReasons = riskReasons(permissions),
        lastUpdatedAt = lastUpdatedAt,
    )

    /**
     * Calibrated so "High risk" stays meaningful: a typical chat or social app (camera, mic,
     * contacts, photos) is Medium; High needs background tracking of critical data or a very
     * broad mix of access. If every app were red, users would learn to ignore the badge.
     */
    const val HIGH_THRESHOLD = 18
    const val MEDIUM_THRESHOLD = 8

    /** Up to [max] plain-language reasons explaining the risk level, most serious first. */
    fun riskReasons(permissions: List<AppPermission>, max: Int = 3): List<String> =
        permissions
            .filter { it.access.isGranted && it.category.riskWeight > 0 }
            .sortedWith(
                compareByDescending<AppPermission> { it.isBackground }
                    .thenBy { it.category.sensitivity.ordinal }
                    .thenByDescending { it.category.riskWeight }
            )
            .take(max)
            .map { p ->
                val noun = p.category.riskNoun
                when (p.access) {
                    AccessLevel.ALWAYS -> "Can use $noun even when the app is closed"
                    AccessLevel.LIMITED -> "Has limited access to $noun"
                    else -> "Can access $noun"
                }
            }

    private val PermissionCategory.riskNoun: String
        get() = when (this) {
            PermissionCategory.LOCATION -> "your location"
            PermissionCategory.CAMERA -> "your camera"
            PermissionCategory.MICROPHONE -> "your microphone"
            PermissionCategory.FILES_PHOTOS -> "your photos and files"
            PermissionCategory.CONTACTS -> "your contacts"
            PermissionCategory.SMS -> "your text messages (including OTPs)"
            PermissionCategory.PHONE -> "your calls and phone details"
            PermissionCategory.CALENDAR -> "your calendar"
            PermissionCategory.BODY_SENSORS -> "your health sensors"
            PermissionCategory.PHYSICAL_ACTIVITY -> "your physical activity"
            PermissionCategory.NEARBY_DEVICES -> "nearby devices"
            PermissionCategory.NOTIFICATIONS -> "notifications"
        }
}
