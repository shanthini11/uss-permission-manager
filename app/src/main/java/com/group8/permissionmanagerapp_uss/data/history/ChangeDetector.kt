package com.group8.permissionmanagerapp_uss.data.history

import com.group8.permissionmanagerapp_uss.data.model.InstalledApp

/**
 * Finds permission changes by comparing the last snapshot with the current state.
 *
 * Android doesn't notify apps when another app's permissions change, so we compare snapshots
 * each time Permission Manager opens and periodically in the background.
 * Pure Kotlin so it can be unit-tested.
 */
object ChangeDetector {

    fun snapshotOf(apps: List<InstalledApp>, now: Long) = PermissionSnapshot(
        apps = apps.associate { app -> app.packageName to app.permissions.associate { it.category to it.access } },
        labels = apps.associate { it.packageName to it.label },
        takenAt = now,
    )

    /**
     * @param previous last snapshot, or null on the very first check (then nothing is reported:
     *   we can't know what changed before we started watching).
     */
    fun diff(
        previous: PermissionSnapshot?,
        apps: List<InstalledApp>,
        now: Long,
        newId: () -> String,
    ): List<PermissionEvent> {
        if (previous == null) return emptyList()
        val events = mutableListOf<PermissionEvent>()
        for (app in apps) {
            val before = previous.apps[app.packageName]
            for (permission in app.permissions) {
                val old = before?.get(permission.category)
                val new = permission.access
                // Newly installed app: only report what it was actually allowed.
                if (before == null && !new.isGranted) continue
                if (old == new) continue
                events += PermissionEvent(
                    id = newId(),
                    timestamp = now,
                    packageName = app.packageName,
                    appLabel = app.label,
                    category = permission.category,
                    from = old,
                    to = new,
                    source = EventSource.DETECTED,
                    isDemo = false,
                )
            }
        }
        return events
    }
}
