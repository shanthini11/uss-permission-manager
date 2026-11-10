package com.group8.permissionmanagerapp_uss

import android.app.Application
import android.content.Context
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.auth.AuthRepository
import com.group8.permissionmanagerapp_uss.data.auth.OtpService
import com.group8.permissionmanagerapp_uss.data.backup.BackupRepository
import com.group8.permissionmanagerapp_uss.data.devices.DeviceIdentity
import com.group8.permissionmanagerapp_uss.data.devices.DeviceRepository
import com.group8.permissionmanagerapp_uss.data.history.HistoryRepository
import com.group8.permissionmanagerapp_uss.data.history.PermissionMonitor
import com.group8.permissionmanagerapp_uss.data.repository.DemoPermissionRepository
import com.group8.permissionmanagerapp_uss.data.repository.DevicePermissionRepository
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import com.group8.permissionmanagerapp_uss.work.Notifier
import com.group8.permissionmanagerapp_uss.work.ReturnWatcher
import com.group8.permissionmanagerapp_uss.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Simple manual dependency container. Kept deliberately small instead of a DI framework
 * so the build stays fast and easy to follow.
 */
class AppContainer(context: Context) {
    /** Long-lived scope for app-wide background tasks (not tied to a screen). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = UserPreferencesRepository(context)
    val devicePermissions = DevicePermissionRepository(context)
    val demoPermissions = DemoPermissionRepository()
    val appsRepository = AppsRepository(
        device = devicePermissions,
        demo = demoPermissions,
        preferences = preferences,
    )
    val notifier = Notifier(context)
    val history = HistoryRepository(context, preferences)
    val permissionMonitor = PermissionMonitor(devicePermissions, history, notifier, preferences)
    val auth = AuthRepository(context)
    val otp = OtpService(BuildConfig.OTP_SERVICE_URL, auth)
    val returnWatcher = ReturnWatcher(context, devicePermissions, notifier, appScope)
    val backup = BackupRepository(context, history, preferences)
    val deviceIdentity = DeviceIdentity(context)
    val devices = DeviceRepository(auth, deviceIdentity, otp, devicePermissions, preferences)
}

class PermissionManagerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()

        container.appScope.launch {
            container.preferences.preferences
                .map { it.periodicReview to it.demoMode }
                .distinctUntilChanged()
                .collect { (periodicReview, demoMode) ->
                    WorkScheduler.schedule(this@PermissionManagerApp, periodicReview)
                    if (demoMode) container.history.seedDemoIfNeeded()
                }
        }
    }
}
