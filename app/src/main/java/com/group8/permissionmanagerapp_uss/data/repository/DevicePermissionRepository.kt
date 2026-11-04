package com.group8.permissionmanagerapp_uss.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads real installed apps and their runtime permission state from [PackageManager].
 *
 * Read-only by design: a normal app cannot grant or revoke other apps' permissions, so changes
 * are handed off to the system Settings screen.
 */
class DevicePermissionRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PermissionRepository {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    override val isDemo: Boolean = false

    override suspend fun loadApps(includeSystemApps: Boolean): List<InstalledApp> =
        withContext(ioDispatcher) {
            installedPackages()
                .asSequence()
                .filter { it.packageName != appContext.packageName }
                .filter { includeSystemApps || isUserFacing(it) }
                .mapNotNull { toInstalledApp(it) }
                .filter { it.permissions.isNotEmpty() }
                .sortedWith(compareByDescending<InstalledApp> { it.riskScore }.thenBy { it.label.lowercase() })
                .toList()
        }

    override suspend fun loadApp(packageName: String): InstalledApp? = withContext(ioDispatcher) {
        runCatching { packageInfo(packageName) }.getOrNull()?.let { toInstalledApp(it) }
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }

    /** Apps the user installed, plus pre-installed apps that appear in the launcher (Chrome, Maps...). */
    private fun isUserFacing(info: PackageInfo): Boolean {
        val appInfo = info.applicationInfo ?: return false
        val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        if (!isSystem || isUpdatedSystem) return true
        return pm.getLaunchIntentForPackage(info.packageName) != null
    }

    private fun toInstalledApp(info: PackageInfo): InstalledApp? {
        val appInfo = info.applicationInfo ?: return null
        val requested = info.requestedPermissions ?: return null
        val flags = info.requestedPermissionsFlags ?: IntArray(requested.size)

        val grants = requested.indices.associate { i ->
            requested[i] to (flags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0)
        }
        val permissions = PermissionResolver.resolve(grants)

        return InstalledApp(
            packageName = info.packageName,
            label = appInfo.loadLabel(pm).toString(),
            isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            permissions = permissions,
            riskScore = PermissionResolver.riskScore(permissions),
            riskLevel = PermissionResolver.riskLevel(permissions),
            lastUpdatedAt = info.lastUpdateTime,
        )
    }
}
