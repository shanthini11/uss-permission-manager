package com.group8.permissionmanagerapp_uss.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.group8.permissionmanagerapp_uss.ui.appdetail.AppDetailScreen
import com.group8.permissionmanagerapp_uss.ui.apps.AppsScreen
import com.group8.permissionmanagerapp_uss.ui.common.ComingSoonScreen
import com.group8.permissionmanagerapp_uss.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerRoot() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { destination.isOn(it.route) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(titleFor(destination, currentTab), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (currentTab == null && destination != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigateSingle(NotificationsRoute) }) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = { navController.navigateSingle(ProfileRoute) }) {
                        Icon(Icons.Outlined.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = { navController.navigateSingle(SettingsRoute) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                TopLevelDestination.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(tab) },
                        icon = {
                            Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null)
                        },
                        label = { Text(tab.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRoute> {
                ComingSoonScreen(
                    "Dashboard",
                    "Your permission overview arrives on Day 2. For now, open the Apps tab to see every app and its permissions.",
                )
            }
            composable<AppsRoute> {
                AppsScreen(onAppClick = { pkg -> navController.navigate(AppDetailRoute(pkg)) })
            }
            composable<CategoriesRoute> {
                ComingSoonScreen("Permission Categories", "Browse apps by Location, Camera, Microphone and more. Arrives on Day 2.")
            }
            composable<HistoryRoute> {
                ComingSoonScreen("Permission History", "A log of every permission change. Arrives on Day 3.")
            }
            composable<NotificationsRoute> {
                ComingSoonScreen("Notifications", "Alerts when an app's permissions change. Arrives on Day 3.")
            }
            composable<ProfileRoute> {
                ComingSoonScreen("Profile", "Your account. Arrives on Day 4 with sign-in.")
            }
            composable<SettingsRoute> { SettingsScreen() }
            composable<AppDetailRoute> { entry ->
                val route = entry.toRoute<AppDetailRoute>()
                AppDetailScreen(packageName = route.packageName, snackbarHostState = snackbarHostState)
            }
        }
    }
}

private fun NavDestination?.isOn(route: Any): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

private fun titleFor(destination: NavDestination?, tab: TopLevelDestination?): String = when {
    tab == TopLevelDestination.HOME -> "Permission Manager"
    tab != null -> tab.label
    destination.isOn(AppDetailRoute("")) -> "App permissions"
    destination.isOn(NotificationsRoute) -> "Notifications"
    destination.isOn(ProfileRoute) -> "Profile"
    destination.isOn(SettingsRoute) -> "Settings"
    else -> "Permission Manager"
}

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateSingle(route: Any) {
    navigate(route) { launchSingleTop = true }
}
