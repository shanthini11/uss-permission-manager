package com.group8.permissionmanagerapp_uss.data

import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.PermissionChoice
import com.group8.permissionmanagerapp_uss.data.repository.DemoPermissionRepository
import com.group8.permissionmanagerapp_uss.data.repository.PermissionRepository
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferences
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** Snapshot of installed apps, tagged with whether it came from Demo mode. */
data class AppsSnapshot(
    val apps: List<InstalledApp>,
    val isDemo: Boolean,
    val showSystemApps: Boolean,
)

/**
 * Single entry point the UI uses for app/permission data. Chooses the real device source or the
 * Demo source based on the user's setting, and re-reads when [refresh] is called
 * (e.g. when the user comes back from the system Settings app).
 */
class AppsRepository(
    private val device: PermissionRepository,
    private val demo: DemoPermissionRepository,
    private val preferences: UserPreferencesRepository,
) {
    private val refreshTick = MutableStateFlow(0)

    fun refresh() {
        refreshTick.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val apps: Flow<AppsSnapshot> =
        combine(preferences.preferences.distinctUntilChanged(), refreshTick) { prefs, _ -> prefs }
            .flatMapLatest { prefs -> flow { emit(load(prefs)) } }

    suspend fun loadApp(packageName: String, demoMode: Boolean): InstalledApp? =
        source(demoMode).loadApp(packageName)

    /**
     * Demo mode only: applies a change directly to the sample data and refreshes every screen.
     * Real apps can only be changed in Android Settings.
     */
    fun applyDemoChange(packageName: String, category: PermissionCategory, choice: PermissionChoice) {
        demo.applyChange(packageName, category, choice)
        refresh()
    }

    private suspend fun load(prefs: UserPreferences): AppsSnapshot {
        val source = source(prefs.demoMode)
        return AppsSnapshot(
            apps = source.loadApps(includeSystemApps = prefs.showSystemApps),
            isDemo = source.isDemo,
            showSystemApps = prefs.showSystemApps,
        )
    }

    private fun source(demoMode: Boolean) = if (demoMode) demo else device
}
