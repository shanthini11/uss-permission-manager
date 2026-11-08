package com.group8.permissionmanagerapp_uss.ui.appdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.group8.permissionmanagerapp_uss.data.model.AppPermission
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionChoice
import com.group8.permissionmanagerapp_uss.ui.components.AccessChip
import com.group8.permissionmanagerapp_uss.ui.components.CategoryIconBox
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

/**
 * The lo-fi "Specific Permission" sheet: why it's needed, the access options, and Apply.
 * Essential info first; full details behind "More about this permission" (R14).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(
    app: InstalledApp,
    permission: AppPermission,
    isDemo: Boolean,
    onDismiss: () -> Unit,
    onApply: (PermissionChoice) -> Unit,
) {
    val category = permission.category
    val current = PermissionChoice.current(permission)
    val options = PermissionChoice.optionsFor(category)
    var selected by rememberSaveable(app.packageName, category) { mutableStateOf(current) }
    var showMore by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: icon, "CAMERA ACCESS", "Instagram · This device", close.
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBox(category, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${category.title.uppercase()} ACCESS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${app.label} · This device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Right now: ", style = MaterialTheme.typography.bodyMedium)
                AccessChip(permission.access)
            }

            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Why apps ask for this", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(category.whyAppsNeedIt, style = MaterialTheme.typography.bodyMedium)

                    TextButton(onClick = { showMore = !showMore }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showMore) "Less" else "More about this permission")
                        Icon(if (showMore) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    if (showMore) {
                        Detail("What it can access", category.whatItAccesses)
                        Detail("If you don't allow it", category.ifYouDeny)
                        Detail("Privacy risk", category.privacyRisk)
                    }
                }
            }

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    OptionRow(
                        choice = option,
                        selected = selected == option,
                        isCurrent = current == option,
                        onSelect = { selected = option },
                    )
                }
            }

            // Lo-fi "Auto-revoke if unused". Android manages this per app in its own Settings.
            SectionCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Auto-revoke if unused", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Android can remove this app's permissions if you don't open it for a few months. " +
                                "Turn on \"Manage app if unused\" on the app's page in Android Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!isDemo && selected != current) {
                Text(
                    "Next, we'll show you exactly what to tap in Android Settings, then check that it worked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
                Button(
                    onClick = { onApply(selected) },
                    enabled = selected != current,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(if (selected == current) "No change" else "Apply setting")
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    choice: PermissionChoice,
    selected: Boolean,
    isCurrent: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(choice.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(choice.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (isCurrent) {
                    Text("Current setting", style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            choice.tag?.let { tag ->
                Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        tag.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Detail(title: String, body: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Step-by-step guide shown before opening Android Settings (real mode only). */
@Composable
fun FinishInSettingsDialog(
    appLabel: String,
    pending: PendingChange,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val categoryName = PermissionChoice.systemCategoryName(pending.category)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish in Android Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For your security, Android only lets its own Settings app change permissions. We'll open $appLabel's page. There:")
                Text("1.  Tap Permissions", fontWeight = FontWeight.SemiBold)
                Text("2.  Tap $categoryName", fontWeight = FontWeight.SemiBold)
                Text("3.  Choose \"${pending.choice.systemOption}\"", fontWeight = FontWeight.SemiBold)
                Text("Then press Back to return here. We'll check that it worked.")
            }
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lo-fi "Permission Updated" / "Permission Not Updated" confirmation (R10, SR9). */
@Composable
fun ChangeResultDialog(
    result: ChangeResult,
    onDone: () -> Unit,
    onRetry: () -> Unit,
) {
    val risk = LocalRiskColors.current
    val where = "${result.appLabel} · ${result.category.title}"
    when (result) {
        is ChangeResult.Updated -> AlertDialog(
            onDismissRequest = onDone,
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = risk.low) },
            title = { Text("Permission updated") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$where is now set to \"${result.choice.label}\".")
                    if (result.appliedInDemo) {
                        Text(
                            "Demo mode: applied to the sample app only. Nothing on your phone changed.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = onDone) { Text("Done") } },
        )

        is ChangeResult.NotUpdated -> AlertDialog(
            onDismissRequest = onDone,
            icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = risk.high) },
            title = { Text("Permission not updated") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (result.actual == null) {
                        Text("We couldn't open Android Settings for ${result.appLabel}. Nothing was changed.")
                    } else {
                        Text("$where is still \"${result.actual.label}\".")
                        Text("You chose \"${result.choice.label}\". Nothing was changed.")
                    }
                }
            },
            confirmButton = { Button(onClick = onRetry) { Text("Try again") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
    }
}
