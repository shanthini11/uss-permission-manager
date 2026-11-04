package com.group8.permissionmanagerapp_uss.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val demoMode: Boolean = false,
    val showSystemApps: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** App settings that are not sensitive (no personal data is stored here). */
class UserPreferencesRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val preferences: Flow<UserPreferences> = store.data.map { p ->
        UserPreferences(
            demoMode = p[DEMO_MODE] ?: false,
            showSystemApps = p[SHOW_SYSTEM_APPS] ?: false,
        )
    }

    suspend fun setDemoMode(enabled: Boolean) {
        store.edit { it[DEMO_MODE] = enabled }
    }

    suspend fun setShowSystemApps(enabled: Boolean) {
        store.edit { it[SHOW_SYSTEM_APPS] = enabled }
    }

    private companion object {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
    }
}
