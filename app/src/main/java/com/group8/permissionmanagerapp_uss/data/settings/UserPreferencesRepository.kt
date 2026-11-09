package com.group8.permissionmanagerapp_uss.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val demoMode: Boolean = false,
    val showSystemApps: Boolean = false,
    /** Lo-fi "Permission Notifications": alerts when an app's permissions change. */
    val notificationsEnabled: Boolean = true,
    /** Lo-fi "Permission History": keep records for the last 30 days. */
    val historyEnabled: Boolean = true,
    /** Lo-fi "Periodic Review": weekly permission-review reminders. */
    val periodicReview: Boolean = true,
    /** Epoch millis of the last successful backup export, or null if never. */
    val lastBackupAt: Long? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** App settings that are not sensitive (no personal data is stored here). */
class UserPreferencesRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val preferences: Flow<UserPreferences> = store.data.map { p ->
        UserPreferences(
            demoMode = p[DEMO_MODE] ?: false,
            showSystemApps = p[SHOW_SYSTEM_APPS] ?: false,
            notificationsEnabled = p[NOTIFICATIONS] ?: true,
            historyEnabled = p[HISTORY] ?: true,
            periodicReview = p[PERIODIC_REVIEW] ?: true,
            lastBackupAt = p[LAST_BACKUP],
        )
    }

    suspend fun setDemoMode(enabled: Boolean) = set(DEMO_MODE, enabled)
    suspend fun setShowSystemApps(enabled: Boolean) = set(SHOW_SYSTEM_APPS, enabled)
    suspend fun setNotificationsEnabled(enabled: Boolean) = set(NOTIFICATIONS, enabled)
    suspend fun setHistoryEnabled(enabled: Boolean) = set(HISTORY, enabled)
    suspend fun setPeriodicReview(enabled: Boolean) = set(PERIODIC_REVIEW, enabled)

    suspend fun setLastBackupAt(time: Long) {
        store.edit { it[LAST_BACKUP] = time }
    }

    /** Restores the review preferences from a backup (not Demo mode or system-app display). */
    suspend fun restoreReviewPreferences(notifications: Boolean, history: Boolean, periodicReview: Boolean) {
        store.edit {
            it[NOTIFICATIONS] = notifications
            it[HISTORY] = history
            it[PERIODIC_REVIEW] = periodicReview
        }
    }

    /** Resets every preference to its default (Settings > Delete Local Data). */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        store.edit { it[key] = value }
    }

    private companion object {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val PERIODIC_REVIEW = booleanPreferencesKey("periodic_review")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }
}
