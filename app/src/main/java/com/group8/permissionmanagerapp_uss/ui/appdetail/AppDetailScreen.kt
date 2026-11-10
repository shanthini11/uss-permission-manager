package com.group8.permissionmanagerapp_uss.ui.appdetail

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.RemoveModerator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.data.model.AppPermission
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.RiskLevel
import com.group8.permissionmanagerapp_uss.data.model.Sensitivity
import com.group8.permissionmanagerapp_uss.ui.auth.rememberIdentityGuard
import com.group8.permissionmanagerapp_uss.ui.components.AccessChip
import com.group8.permissionmanagerapp_uss.ui.components.AppAvatar
import com.group8.permissionmanagerapp_uss.ui.components.CategoryIconBox
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.PreinstalledChip
import com.group8.permissionmanagerapp_uss.ui.components.RiskBadge
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.components.SensitivityBadge
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors
import kotlinx.coroutines.launch

/**
 * Lo-fi "Specific App Page". Tapping a permission opens the permission sheet.
 *
 * @param focusCategory if set (e.g. coming from a category list), that permission's sheet opens immediately.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    focusCategory: PermissionCategory? = null,
    viewModel: AppDetailViewModel = viewModel(
        key = "app-detail-$packageName",
        factory = AppDetailViewModel.factory(packageName),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheetCategory by rememberSaveable(packageName) { mutableStateOf(focusCategory) }
    val identity = rememberIdentityGuard()
    identity.Dialog()

    // The "Tap to return" banner needs notification permission (Android 13+). Ask once, just before
    // going to Settings; whatever the answer, continue to Settings.
    var afterNotificationPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotificationPrompt?.invoke()
        afterNotificationPrompt = null
    }
    val goToSettings: (() -> Unit) -> Unit = { proceed ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            afterNotificationPrompt = proceed
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            proceed()
        }
    }

    LifecycleResumeEffect(packageName) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val openSettings: () -> Unit = {
        if (state.isDemo) {
            scope.launch {
                snackbarHostState.showSnackbar("Demo app: with Demo mode off, this opens the app's page in Android Settings.")
            }
        } else if (!openAppSystemSettings(context, packageName)) {
            scope.launch { snackbarHostState.showSnackbar("Couldn't open Android Settings for this app.") }
        }
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

        else -> {
            val app = state.app!!
            AppDetailContent(
                app = app,
                isDemo = state.isDemo,
                onPermissionClick = { sheetCategory = it.category },
                onOpenSystemSettings = openSettings,
                onRevokeAll = { identity.run("revoke all of ${app.label}'s permissions", alwaysAsk = true) { viewModel.requestRevokeAll() } },
                modifier = modifier,
            )

            val sheetPermission = sheetCategory?.let { app.accessFor(it) }
            if (sheetPermission != null) {
                PermissionSheet(
                    app = app,
                    permission = sheetPermission,
                    isDemo = state.isDemo,
                    onDismiss = { sheetCategory = null },
                    onApply = { choice ->
                        sheetCategory = null
                        identity.run("change this permission") {
                            viewModel.requestChange(sheetPermission.category, choice)
                        }
                    },
                )
            }

            state.guide?.let { pending ->
                FinishInSettingsDialog(
                    appLabel = app.label,
                    pending = pending,
                    onOpenSettings = {
                        // Ask for notifications first: the pending change must only be recorded right before
                        // Settings opens, otherwise returning from the prompt would trigger the check early.
                        goToSettings {
                            viewModel.onGuideConfirmed()
                            if (!openAppSystemSettings(context, packageName)) viewModel.onSettingsOpenFailed()
                        }
                    },
                    onDismiss = viewModel::dismissGuide,
                )
            }

            state.result?.let { result ->
                ChangeResultDialog(result = result, onDone = viewModel::dismissResult, onRetry = viewModel::retry)
            }

            if (state.confirmRevokeAll) {
                ConfirmRevokeAllDialog(app, onConfirm = viewModel::confirmRevokeAll, onDismiss = viewModel::cancelRevokeAll)
            }
            state.revokeAllGuide?.let { categories ->
                RevokeAllGuideDialog(
                    appLabel = app.label,
                    categories = categories,
                    onOpenSettings = {
                        goToSettings {
                            viewModel.onRevokeAllGuideConfirmed()
                            if (!openAppSystemSettings(context, packageName)) viewModel.onRevokeAllSettingsFailed()
                        }
                    },
                    onDismiss = viewModel::cancelRevokeAll,
                )
            }
            state.revokeAllResult?.let { result ->
                RevokeAllResultDialog(result, onDone = viewModel::dismissRevokeAllResult, onRetry = viewModel::retryRevokeAll)
            }
        }
    }
}

/**
 * Opens Android's own App info page for [packageName]. Android only lets the system change
 * another app's permissions, so the final switch happens there.
 */
fun openAppSystemSettings(context: Context, packageName: String): Boolean = try {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    // From an Activity, open Settings in our own task: the "Tap to return" banner (CLEAR_TOP) can then
    // close all the Settings pages in one go, and Back eventually lands in our app.
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
private fun AppDetailContent(
    app: InstalledApp,
    isDemo: Boolean,
    onPermissionClick: (AppPermission) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onRevokeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isDemo) item { DemoBanner() }

        item { AppHeader(app, onOpenSystemSettings, onRevokeAll) }
        item { SummaryRow(app) }
        if (app.riskReasons.isNotEmpty()) item { RiskReasonsCard(app) }

        val granted = app.permissions.filter { it.access.isGranted }
        val denied = app.permissions.filterNot { it.access.isGranted }

        if (granted.isNotEmpty()) {
            item { SectionTitle("Allowed (${granted.size})", "Tap a permission to understand it or change it") }
            items(granted, key = { "g-" + it.category.name }) { PermissionRow(it) { onPermissionClick(it) } }
        }
        if (denied.isNotEmpty()) {
            item { SectionTitle("Not allowed (${denied.size})", "Requested by the app, but you haven't allowed it") }
            items(denied, key = { "d-" + it.category.name }) { PermissionRow(it) { onPermissionClick(it) } }
        }
    }
}

@Composable
private fun AppHeader(app: InstalledApp, onOpenSystemSettings: () -> Unit, onRevokeAll: () -> Unit) {
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(packageName = app.packageName, label = app.label, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RiskBadge(app.riskLevel)
                        if (app.isSystemApp) PreinstalledChip()
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // Lo-fi: "Revoke All" and "View OS" side by side.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRevokeAll,
                    enabled = app.grantedPermissions.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.RemoveModerator, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Revoke all")
                }
                OutlinedButton(onClick = onOpenSystemSettings, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("View in OS")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(app: InstalledApp) {
    val granted = app.grantedPermissions
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Allowed", granted.size.toString(), Modifier.weight(1f))
        StatTile("Critical", granted.count { it.category.sensitivity == Sensitivity.CRITICAL }.toString(), Modifier.weight(1f))
        StatTile("Background", granted.count { it.isBackground }.toString(), Modifier.weight(1f))
    }
}

/** Explains the risk badge in plain words, so the rating is never a mystery (SR5). */
@Composable
private fun RiskReasonsCard(app: InstalledApp) {
    val risk = LocalRiskColors.current
    val tint = when (app.riskLevel) {
        RiskLevel.HIGH -> risk.high
        RiskLevel.MEDIUM -> risk.medium
        RiskLevel.LOW -> risk.low
    }
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Text("Why it's ${app.riskLevel.label.lowercase()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            app.riskReasons.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            if (app.isSystemApp) {
                Text(
                    "This app came with your phone and usually needs this access to work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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

@Composable
private fun PermissionRow(permission: AppPermission, onClick: () -> Unit) {
    val category = permission.category
    SectionCard {
        Row(
            Modifier
                .clickable(role = Role.Button, onClickLabel = "Review ${category.title}", onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconBox(category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                AccessChip(permission.access)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
