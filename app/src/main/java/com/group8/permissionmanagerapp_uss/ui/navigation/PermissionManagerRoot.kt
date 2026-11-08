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
import androidx.compose.runtime.LaunchedEffect
import com.group8.permissionmanagerapp_uss.ui.history.HistoryScreen
import com.group8.permissionmanagerapp_uss.ui.history.NotificationsScreen
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
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.ui.overview.BackgroundRiskScreen
import com.group8.permissionmanagerapp_uss.ui.overview.CategoriesScreen
import com.group8.permissionmanagerapp_uss.ui.overview.HomeScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerRoot(openNotificationsRequest: Int = 0) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Tapping a system notification opens the Notifications screen.
    LaunchedEffect(openNotificationsRequest) {
        if (openNotificationsRequest > 0) navController.navigateSingle(NotificationsRoute)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // Tab whose root screen is showing right now (no back arrow on these).
    val currentTab = TopLevelDestination.entries.firstOrNull { destination.isOn(it.route) }
    // Tab the user is "inside", even on a pushed screen like Settings, so the bar stays highlighted.
    // Home is always at the bottom of the stack, so prefer any other tab that is present.
    // Recomputed whenever backStackEntry changes.
    val owningTab = backStackEntry?.let {
        TopLevelDestination.entries.lastOrNull { tab -> navController.hasInBackStack(tab.route) }
    }

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
                    val selected = owningTab == tab
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
                HomeScreen(
                    onAppClick = { pkg -> navController.navigate(AppDetailRoute(pkg)) },
                    onOpenCategories = { navController.navigateToTab(TopLevelDestination.CATEGORIES) },
                    onOpenBackgroundRisk = { navController.navigateSingle(BackgroundRiskRoute) },
                    onOpenApps = { navController.navigateToTab(TopLevelDestination.APPS) },
                )
            }
            composable<AppsRoute> {
                AppsScreen(onAppClick = { pkg -> navController.navigate(AppDetailRoute(pkg)) })
            }
            composable<CategoriesRoute> {
                CategoriesScreen(
                    onAppClick = { pkg, category -> navController.navigate(AppDetailRoute(pkg, category.name)) },
                )
            }
            composable<BackgroundRiskRoute> {
                BackgroundRiskScreen(
                    onAppClick = { pkg, category -> navController.navigate(AppDetailRoute(pkg, category.name)) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    onReviewPermission = { pkg, category -> navController.navigate(AppDetailRoute(pkg, category.name)) },
                )
            }
            composable<NotificationsRoute> {
                NotificationsScreen(
                    onViewPermission = { pkg, category -> navController.navigate(AppDetailRoute(pkg, category.name)) },
                    onOpenHistory = { navController.navigateToTab(TopLevelDestination.HISTORY) },
                    onStartReview = { navController.navigateToTab(TopLevelDestination.CATEGORIES) },
                )
            }
            composable<ProfileRoute> {
                ComingSoonScreen("Profile", "Your account. Arrives on Day 4 with sign-in.")
            }
            composable<SettingsRoute> { SettingsScreen() }
            composable<AppDetailRoute> { entry ->
                val route = entry.toRoute<AppDetailRoute>()
                AppDetailScreen(
                    packageName = route.packageName,
                    snackbarHostState = snackbarHostState,
                    focusCategory = route.focusCategory?.let { name ->
                        PermissionCategory.entries.firstOrNull { it.name == name }
                    },
                )
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
    destination.isOn(BackgroundRiskRoute) -> "Background risk"
    destination.isOn(NotificationsRoute) -> "Notifications"
    destination.isOn(ProfileRoute) -> "Profile"
    destination.isOn(SettingsRoute) -> "Settings"
    else -> "Permission Manager"
}

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    // Re-selecting the tab you're already in (e.g. from Settings opened inside Apps) returns to its
    // main screen. Without this, restoreState would restore the same stack and nothing would happen.
    val owningTab = TopLevelDestination.entries.lastOrNull { hasInBackStack(it.route) }
    if (owningTab == tab) {
        popBackStack(tab.route, inclusive = false)
        return
    }
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.hasInBackStack(route: Any): Boolean =
    try {
        getBackStackEntry(route)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

private fun NavHostController.navigateSingle(route: Any) {
    navigate(route) { launchSingleTop = true }
}
