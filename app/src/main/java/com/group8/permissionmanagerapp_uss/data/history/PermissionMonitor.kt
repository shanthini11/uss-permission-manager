package com.group8.permissionmanagerapp_uss.data.history

import com.group8.permissionmanagerapp_uss.data.repository.PermissionRepository
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import com.group8.permissionmanagerapp_uss.work.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Checks the real device for permission changes (always the real device, even in Demo mode,
 * so History keeps working in the background).
 */
class PermissionMonitor(
    private val device: PermissionRepository,
    private val history: HistoryRepository,
    private val notifier: Notifier,
    private val preferences: UserPreferencesRepository,
) {
    private val mutex = Mutex()

    /** @param postNotifications true from the background worker; false when the app is open. */
    suspend fun check(postNotifications: Boolean): List<PermissionEvent> = mutex.withLock {
        val apps = device.loadApps(includeSystemApps = false)
        val events = history.recordDetected(apps)
        if (postNotifications && preferences.preferences.first().notificationsEnabled) {
            notifier.notifyChanges(events)
        }
        events
    }
}
