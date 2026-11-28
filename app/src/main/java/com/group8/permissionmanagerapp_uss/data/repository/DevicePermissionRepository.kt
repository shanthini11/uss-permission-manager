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
            // Faster loading (peer-testing finding): one launcher query instead of one per system app,
            // and app names (the slowest call) are only loaded for apps that have tracked permissions.
            val launcherApps = if (includeSystemApps) emptySet() else launcherPackages()
            installedPackages()
                .asSequence()
                .filter { it.packageName != appContext.packageName }
                .filter { includeSystemApps || isUserFacing(it, launcherApps) }
                .mapNotNull { toInstalledApp(it) }
                .sortedWith(InstalledApp.RiskOrder)
                .toList()
        }

    /** Packages with a launcher icon, from a single query. */
    @Suppress("DEPRECATION")
    private fun launcherPackages(): Set<String> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        return list.mapNotNull { it.activityInfo?.packageName }.toSet()
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
    private fun isUserFacing(info: PackageInfo, launcherApps: Set<String>): Boolean {
        val appInfo = info.applicationInfo ?: return false
        val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        if (!isSystem || isUpdatedSystem) return true
        return info.packageName in launcherApps
    }

    private fun toInstalledApp(info: PackageInfo): InstalledApp? {
        val appInfo = info.applicationInfo ?: return null
        val requested = info.requestedPermissions ?: return null
        val flags = info.requestedPermissionsFlags ?: IntArray(requested.size)

        val grants = requested.indices.associate { i ->
            requested[i] to (flags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0)
        }
        val permissions = PermissionResolver.resolve(grants, Build.VERSION.SDK_INT)
        if (permissions.isEmpty()) return null // skip the slow label lookup for apps we don't show
        return PermissionResolver.buildApp(
            packageName = info.packageName,
            label = appInfo.loadLabel(pm).toString(),
            isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            permissions = permissions,
            lastUpdatedAt = info.lastUpdateTime,
        )
    }
}
