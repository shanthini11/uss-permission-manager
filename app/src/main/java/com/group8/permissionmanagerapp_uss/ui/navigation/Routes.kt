package com.group8.permissionmanagerapp_uss.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe navigation routes.
@Serializable data object HomeRoute
@Serializable data object AppsRoute
@Serializable data object CategoriesRoute
@Serializable data object HistoryRoute
@Serializable data object NotificationsRoute
@Serializable data object ProfileRoute
@Serializable data object SettingsRoute
@Serializable data class AppDetailRoute(val packageName: String)

/**
 * Bottom navigation tabs. Each tab has a text label as well as an icon: in the lo-fi study,
 * icon-only navigation (the lock icon / "Top Access") was hard to discover (UP3, UP4).
 */
enum class TopLevelDestination(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("Home", HomeRoute, Icons.Filled.Home, Icons.Outlined.Home),
    APPS("Apps", AppsRoute, Icons.Filled.Apps, Icons.Outlined.Apps),
    CATEGORIES("Categories", CategoriesRoute, Icons.Filled.Lock, Icons.Outlined.Lock),
    HISTORY("History", HistoryRoute, Icons.Filled.History, Icons.Outlined.History),
}
