package com.group8.permissionmanagerapp_uss.data.repository

import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionResolver

/**
 * Sample apps for Demo mode, so the app can be evaluated on a fresh emulator with few apps installed.
 *
 * Demo apps are defined as raw Android permission grants and go through the same
 * [PermissionResolver] as real apps, so risk scores and labels behave identically.
 * All package names use the reserved `demo.` prefix so they can never collide with real apps.
 */
class DemoPermissionRepository(
    private val now: () -> Long = System::currentTimeMillis,
) : PermissionRepository {

    override val isDemo: Boolean = true

    override suspend fun loadApps(includeSystemApps: Boolean): List<InstalledApp> =
        demoApps()
            .filter { includeSystemApps || !it.isSystemApp }
            .sortedWith(compareByDescending<InstalledApp> { it.riskScore }.thenBy { it.label.lowercase() })

    override suspend fun loadApp(packageName: String): InstalledApp? =
        demoApps().firstOrNull { it.packageName == packageName }

    private fun demoApps(): List<InstalledApp> = specs.map { spec ->
        val perms = PermissionResolver.resolve(spec.grants)
        InstalledApp(
            packageName = spec.packageName,
            label = spec.label,
            isSystemApp = spec.isSystem,
            permissions = perms,
            riskScore = PermissionResolver.riskScore(perms),
            riskLevel = PermissionResolver.riskLevel(perms),
            lastUpdatedAt = now() - spec.daysSinceUpdate * DAY_MS,
        )
    }

    private data class Spec(
        val packageName: String,
        val label: String,
        val grants: Map<String, Boolean>,
        val daysSinceUpdate: Long,
        val isSystem: Boolean = false,
    )

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val P = "android.permission."

        private fun g(vararg granted: String) = granted.associate { P + it to true }
        private fun d(vararg denied: String) = denied.associate { P + it to false }

        private val specs = listOf(
            Spec(
                "demo.instagram", "Instagram",
                g("CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
                    "ACCESS_BACKGROUND_LOCATION", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "POST_NOTIFICATIONS") +
                    d("READ_CONTACTS"),
                daysSinceUpdate = 3,
            ),
            Spec(
                "demo.tiktok", "TikTok",
                g("CAMERA", "RECORD_AUDIO", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_CONTACTS",
                    "POST_NOTIFICATIONS") + d("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"),
                daysSinceUpdate = 1,
            ),
            Spec(
                "demo.whatsapp", "WhatsApp",
                g("CAMERA", "RECORD_AUDIO", "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS",
                    "READ_MEDIA_VISUAL_USER_SELECTED", "POST_NOTIFICATIONS", "READ_PHONE_STATE",
                    "BLUETOOTH_CONNECT") + d("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_MEDIA_IMAGES"),
                daysSinceUpdate = 6,
            ),
            Spec(
                "demo.maps", "Google Maps",
                g("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
                    "RECORD_AUDIO", "POST_NOTIFICATIONS", "ACTIVITY_RECOGNITION") + d("CAMERA", "READ_CONTACTS"),
                daysSinceUpdate = 10,
                isSystem = true,
            ),
            Spec(
                "demo.spotify", "Spotify",
                g("RECORD_AUDIO", "BLUETOOTH_CONNECT", "POST_NOTIFICATIONS") + d("CAMERA", "READ_MEDIA_AUDIO"),
                daysSinceUpdate = 14,
            ),
            Spec(
                "demo.youtube", "YouTube",
                g("POST_NOTIFICATIONS") + d("CAMERA", "RECORD_AUDIO", "READ_MEDIA_VIDEO"),
                daysSinceUpdate = 20,
                isSystem = true,
            ),
            Spec(
                "demo.chrome", "Chrome",
                g("ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS") +
                    d("ACCESS_FINE_LOCATION", "CAMERA", "RECORD_AUDIO"),
                daysSinceUpdate = 8,
                isSystem = true,
            ),
            Spec(
                "demo.zomato", "Food Delivery",
                g("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS") +
                    d("READ_CONTACTS", "CAMERA"),
                daysSinceUpdate = 4,
            ),
            Spec(
                "demo.fitness", "Fitness Tracker",
                g("ACTIVITY_RECOGNITION", "BODY_SENSORS", "BODY_SENSORS_BACKGROUND", "BLUETOOTH_SCAN",
                    "BLUETOOTH_CONNECT", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"),
                daysSinceUpdate = 30,
            ),
            // Deliberately over-privileged app: shows why least privilege (SR3) and risk markers matter.
            Spec(
                "demo.flashlight", "Super Flashlight",
                g("CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
                    "ACCESS_BACKGROUND_LOCATION", "READ_CONTACTS", "READ_SMS", "READ_CALL_LOG", "READ_PHONE_STATE"),
                daysSinceUpdate = 2,
            ),
            Spec(
                "demo.game", "Puzzle Game",
                g("RECORD_AUDIO", "POST_NOTIFICATIONS") + d("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"),
                daysSinceUpdate = 45,
            ),
            Spec(
                "demo.bank", "Bank App",
                g("CAMERA", "RECEIVE_SMS", "READ_PHONE_STATE", "POST_NOTIFICATIONS") +
                    d("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_CONTACTS"),
                daysSinceUpdate = 12,
            ),
        )
    }
}
