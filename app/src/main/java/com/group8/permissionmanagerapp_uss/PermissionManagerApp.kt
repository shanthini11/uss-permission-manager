package com.group8.permissionmanagerapp_uss

import android.app.Application
import android.content.Context
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.repository.DemoPermissionRepository
import com.group8.permissionmanagerapp_uss.data.repository.DevicePermissionRepository
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository

/**
 * Simple manual dependency container. Kept deliberately small instead of a DI framework
 * so the build stays fast and easy to follow.
 */
class AppContainer(context: Context) {
    val preferences = UserPreferencesRepository(context)
    val appsRepository = AppsRepository(
        device = DevicePermissionRepository(context),
        demo = DemoPermissionRepository(),
        preferences = preferences,
    )
}

class PermissionManagerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
