package com.group8.permissionmanagerapp_uss.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.PermissionChoice
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

/** Warning page before Revoke All; participants said this made them confident (lo-fi study 8.8). */
@Composable
fun ConfirmRevokeAllDialog(app: InstalledApp, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val risk = LocalRiskColors.current
    val granted = app.grantedPermissions
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = risk.high) },
        title = { Text("Revoke all permissions?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${app.label} will lose access to:")
                granted.forEach { Text("•  ${it.category.title}", fontWeight = FontWeight.SemiBold) }
                Text(
                    "Features that need these may stop working until you allow them again. " +
                        "The app may ask for them next time you use it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = risk.high),
            ) { Text("Revoke all") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Real mode: which switches to turn off in Android Settings. */
@Composable
fun RevokeAllGuideDialog(
    appLabel: String,
    categories: List<PermissionCategory>,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish in Android Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Android only lets its own Settings app change permissions. On $appLabel's page, tap Permissions, then set each of these to \"Don't allow\":")
                categories.forEach { Text("•  ${PermissionChoice.systemCategoryName(it)}", fontWeight = FontWeight.SemiBold) }
                Text("Then press Back. We'll check which ones were turned off.")
            }
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lo-fi "Permission Revoked" / "Permission Not Revoked". */
@Composable
fun RevokeAllResultDialog(result: RevokeAllResult, onDone: () -> Unit, onRetry: () -> Unit) {
    val risk = LocalRiskColors.current
    if (result.complete) {
        AlertDialog(
            onDismissRequest = onDone,
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = risk.low) },
            title = { Text("Permissions revoked") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${result.appLabel} no longer has access to ${result.revoked.joinToString { it.title }}.")
                    if (result.appliedInDemo) {
                        Text("Demo mode: applied to the sample app only. Nothing on your phone changed.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { Button(onClick = onDone) { Text("Done") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDone,
            icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = risk.high) },
            title = { Text(if (result.revoked.isEmpty()) "Permissions not revoked" else "Some permissions not revoked") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (result.revoked.isNotEmpty()) Text("Revoked: ${result.revoked.joinToString { it.title }}")
                    Text("Still allowed: ${result.stillAllowed.joinToString { it.title }}", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { Button(onClick = onRetry) { Text("Try again") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Done") } },
        )
    }
}
