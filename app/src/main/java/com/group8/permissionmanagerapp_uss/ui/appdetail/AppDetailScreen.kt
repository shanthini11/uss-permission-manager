package com.group8.permissionmanagerapp_uss.ui.appdetail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.data.model.AppPermission
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.Sensitivity
import com.group8.permissionmanagerapp_uss.ui.components.AccessChip
import com.group8.permissionmanagerapp_uss.ui.components.AppAvatar
import com.group8.permissionmanagerapp_uss.ui.components.CategoryIconBox
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.RiskBadge
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.components.SensitivityBadge
import kotlinx.coroutines.launch

@Composable
fun AppDetailScreen(
    packageName: String,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = viewModel(
        key = "app-detail-$packageName",
        factory = AppDetailViewModel.factory(packageName),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(packageName) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        state.notFound || state.app == null -> EmptyState(
            title = "App not found",
            message = "This app may have been uninstalled. Go back to see your current apps.",
            modifier = modifier,
        )

        else -> AppDetailContent(
            app = state.app!!,
            isDemo = state.isDemo,
            onOpenSystemSettings = {
                if (state.isDemo) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "This is a demo app. With Demo mode off, this opens the app's page in system Settings."
                        )
                    }
                } else if (!openAppSystemSettings(context, packageName)) {
                    scope.launch { snackbarHostState.showSnackbar("Couldn't open system Settings for this app.") }
                }
            },
            modifier = modifier,
        )
    }
}

/**
 * Opens Android's own permission page for [packageName]. Android only lets the system change
 * another app's permissions, so this is how "View in OS settings" works.
 */
fun openAppSystemSettings(context: Context, packageName: String): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
private fun AppDetailContent(
    app: InstalledApp,
    isDemo: Boolean,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isDemo) item { DemoBanner() }

        item { AppHeader(app, onOpenSystemSettings) }
        item { SummaryRow(app) }

        val granted = app.permissions.filter { it.access.isGranted }
        val denied = app.permissions.filterNot { it.access.isGranted }

        if (granted.isNotEmpty()) {
            item { SectionTitle("Allowed (${granted.size})", "Tap a permission to see what it means") }
            items(granted, key = { "g-" + it.category.name }) { PermissionRow(it) }
        }
        if (denied.isNotEmpty()) {
            item { SectionTitle("Not allowed (${denied.size})", "Requested by the app, but you haven't allowed it") }
            items(denied, key = { "d-" + it.category.name }) { PermissionRow(it) }
        }
    }
}

@Composable
private fun AppHeader(app: InstalledApp, onOpenSystemSettings: () -> Unit) {
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(packageName = app.packageName, label = app.label, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    RiskBadge(app.riskLevel)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenSystemSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Change in system Settings")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Android only allows its own Settings app to change permissions. We'll take you there and " +
                    "show the result when you come back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(app: InstalledApp) {
    val granted = app.grantedPermissions
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Allowed", granted.size.toString(), Modifier.weight(1f))
        StatTile(
            "Critical",
            granted.count { it.category.sensitivity == Sensitivity.CRITICAL }.toString(),
            Modifier.weight(1f),
        )
        StatTile("Background", granted.count { it.isBackground }.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One permission. Essential info (name, access, short description) shows first; the full
 * explanation is revealed on tap (progressive disclosure, R14).
 */
@Composable
private fun PermissionRow(permission: AppPermission) {
    var expanded by rememberSaveable(permission.category) { mutableStateOf(false) }
    val category = permission.category

    SectionCard(Modifier.animateContentSize()) {
        Column(
            Modifier
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide details" else "Show details") {
                    expanded = !expanded
                }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBox(category)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        SensitivityBadge(category.sensitivity)
                    }
                    Text(
                        permission.detail ?: category.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            Spacer(Modifier.height(8.dp))
            AccessChip(permission.access)

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Explanation("What it can access", category.whatItAccesses)
                Explanation("Why apps ask for it", category.whyAppsNeedIt)
                Explanation("If you don't allow it", category.ifYouDeny)
                Explanation("Privacy risk", category.privacyRisk)
            }
        }
    }
}

@Composable
private fun Explanation(title: String, body: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
