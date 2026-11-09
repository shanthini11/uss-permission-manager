package com.group8.permissionmanagerapp_uss.data.history

import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory

/** Sample history for Demo mode: a week of realistic changes for the demo apps. */
internal object DemoHistory {
    private const val HOUR = 60L * 60 * 1000
    private const val DAY = 24 * HOUR

    private data class E(
        val ago: Long,
        val pkg: String,
        val label: String,
        val cat: PermissionCategory,
        val from: AccessLevel?,
        val to: AccessLevel,
        val source: EventSource = EventSource.DETECTED,
    )

    private val samples = listOf(
        E(1 * HOUR, "demo.instagram", "Instagram", PermissionCategory.CAMERA, AccessLevel.NOT_ALLOWED, AccessLevel.WHILE_IN_USE),
        E(3 * HOUR, "demo.whatsapp", "WhatsApp", PermissionCategory.MICROPHONE, AccessLevel.NOT_ALLOWED, AccessLevel.WHILE_IN_USE),
        E(5 * HOUR, "demo.maps", "Google Maps", PermissionCategory.LOCATION, AccessLevel.WHILE_IN_USE, AccessLevel.ALWAYS),
        E(7 * HOUR, "demo.chrome", "Chrome", PermissionCategory.LOCATION, AccessLevel.WHILE_IN_USE, AccessLevel.LIMITED, EventSource.IN_APP),
        E(1 * DAY + 2 * HOUR, "demo.spotify", "Spotify", PermissionCategory.MICROPHONE, AccessLevel.NOT_ALLOWED, AccessLevel.WHILE_IN_USE),
        E(1 * DAY + 6 * HOUR, "demo.game", "Puzzle Game", PermissionCategory.LOCATION, AccessLevel.WHILE_IN_USE, AccessLevel.NOT_ALLOWED, EventSource.IN_APP),
        E(2 * DAY, "demo.tiktok", "TikTok", PermissionCategory.FILES_PHOTOS, AccessLevel.NOT_ALLOWED, AccessLevel.ALLOWED),
        E(2 * DAY + 3 * HOUR, "demo.tiktok", "TikTok", PermissionCategory.LOCATION, AccessLevel.WHILE_IN_USE, AccessLevel.NOT_ALLOWED),
        E(3 * DAY, "demo.flashlight", "Super Flashlight", PermissionCategory.CONTACTS, AccessLevel.NOT_ALLOWED, AccessLevel.ALLOWED),
        E(3 * DAY + 1 * HOUR, "demo.flashlight", "Super Flashlight", PermissionCategory.SMS, AccessLevel.NOT_ALLOWED, AccessLevel.ALLOWED),
        E(3 * DAY + 2 * HOUR, "demo.flashlight", "Super Flashlight", PermissionCategory.LOCATION, AccessLevel.NOT_ALLOWED, AccessLevel.ALWAYS),
        E(4 * DAY, "demo.bank", "Bank App", PermissionCategory.CAMERA, AccessLevel.NOT_ALLOWED, AccessLevel.WHILE_IN_USE),
        E(4 * DAY + 5 * HOUR, "demo.bank", "Bank App", PermissionCategory.CONTACTS, AccessLevel.ALLOWED, AccessLevel.NOT_ALLOWED, EventSource.IN_APP),
        E(5 * DAY, "demo.fitness", "Fitness Tracker", PermissionCategory.BODY_SENSORS, AccessLevel.WHILE_IN_USE, AccessLevel.ALWAYS),
        E(5 * DAY + 4 * HOUR, "demo.zomato", "Food Delivery", PermissionCategory.CONTACTS, AccessLevel.NOT_ALLOWED, AccessLevel.NOT_ALLOWED),
        E(6 * DAY, "demo.whatsapp", "WhatsApp", PermissionCategory.FILES_PHOTOS, AccessLevel.ALLOWED, AccessLevel.LIMITED, EventSource.IN_APP),
        E(6 * DAY + 3 * HOUR, "demo.youtube", "YouTube", PermissionCategory.CAMERA, AccessLevel.WHILE_IN_USE, AccessLevel.NOT_ALLOWED),
    )

    fun events(now: Long): List<PermissionEvent> =
        samples.filter { it.from != it.to }.mapIndexed { i, e ->
            PermissionEvent(
                id = "demo-$i",
                timestamp = now - e.ago,
                packageName = e.pkg,
                appLabel = e.label,
                category = e.cat,
                from = e.from,
                to = e.to,
                source = e.source,
                isDemo = true,
                // Older sample alerts start as already seen, so Notifications looks realistic.
                dismissed = e.ago > 2 * DAY,
            )
        } + PermissionEvent(
            id = "demo-review",
            timestamp = now - 20 * HOUR,
            packageName = "",
            appLabel = com.group8.permissionmanagerapp_uss.AppInfo.NAME,
            category = null,
            from = null,
            to = null,
            source = EventSource.REVIEW_REMINDER,
            isDemo = true,
        )
}
