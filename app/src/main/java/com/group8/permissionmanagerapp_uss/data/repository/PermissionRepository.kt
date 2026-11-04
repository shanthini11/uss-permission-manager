package com.group8.permissionmanagerapp_uss.data.repository

import com.group8.permissionmanagerapp_uss.data.model.InstalledApp

/** Source of installed apps and their permissions. */
interface PermissionRepository {
    /** True when the data is sample data (Demo mode), so the UI can label it clearly. */
    val isDemo: Boolean

    suspend fun loadApps(includeSystemApps: Boolean): List<InstalledApp>

    suspend fun loadApp(packageName: String): InstalledApp?
}
