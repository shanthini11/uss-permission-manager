package com.group8.permissionmanagerapp_uss.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.data.auth.InputValidation
import com.group8.permissionmanagerapp_uss.ui.auth.ErrorBanner
import com.group8.permissionmanagerapp_uss.ui.auth.PasswordField
import com.group8.permissionmanagerapp_uss.ui.auth.rememberIdentityGuard
import com.group8.permissionmanagerapp_uss.ui.common.SecureScreen
import com.group8.permissionmanagerapp_uss.ui.common.TimeFormat
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lo-fi "Backup and Restore" (refinement #11). */
@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val context = LocalContext.current
    val step by viewModel.step.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val identity = rememberIdentityGuard()
    identity.Dialog()

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        viewModel.onExportLocationPicked(it)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onImportFilePicked(it)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Protect and restore your Permission Tracker preferences and permission history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            Column {
                Section("LOCAL BACKUP") {
                    Text(
                        "Your backup is saved as a file wherever you choose. It's encrypted with a password you pick. " +
                            "Backups contain your permission history, so store them somewhere safe.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider()
                Section("WHAT IS INCLUDED") {
                    Included("Permission history (last 30 days)")
                    Included("App preferences")
                    Included("Permission review preferences")
                }
                HorizontalDivider()
                Section("LAST BACKUP") {
                    Text(
                        lastBackup?.let { TimeFormat.dateTime(context, it) } ?: "Never",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(
            onClick = { identity.run("create a backup") { viewModel.startExport() } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Export backup") }
        OutlinedButton(
            onClick = { identity.run("restore a backup") { openLauncher.launch(arrayOf("application/octet-stream", "*/*")) } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Import backup") }
        SectionCard {
            Text(
                "Importing a backup does not change Android's permissions. Review them in Android Settings afterwards.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }
    }

    when (val s = step) {
        BackupStep.Idle -> Unit
        BackupStep.Working -> WorkingDialog()
        BackupStep.ChoosePassword -> ChooseBackupPasswordDialog(
            onDismiss = viewModel::cancel,
            onConfirm = { password ->
                viewModel.onExportPasswordChosen(password)
                saveLauncher.launch("PermissionTracker-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.ptbackup")
            },
        )
        BackupStep.Created -> ResultDialog(
            success = true,
            title = "Backup created",
            message = "Your encrypted backup was saved. Keep the file and its password safe: we can't recover a forgotten password.",
            onDone = viewModel::cancel,
        )
        is BackupStep.CreateError -> ResultDialog(
            success = false,
            title = "Backup not created",
            message = s.message,
            onDone = viewModel::cancel,
            onRetry = viewModel::startExport,
        )
        is BackupStep.EnterPassword -> EnterBackupPasswordDialog(s.error, onDismiss = viewModel::cancel, onUnlock = viewModel::unlock)
        is BackupStep.Review -> ReviewBackupDialog(s, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::cancel)
        is BackupStep.Restored -> ResultDialog(
            success = true,
            title = "Backup restored",
            message = "${s.added} history ${if (s.added == 1) "entry was" else "entries were"} added and your preferences were restored.\n\n" +
                "Android's app permissions were NOT changed. Review them in Android Settings if needed.",
            onDone = viewModel::cancel,
        )
        is BackupStep.RestoreError -> ResultDialog(
            success = false,
            title = "Backup not restored",
            message = s.message,
            onDone = viewModel::cancel,
            onRetry = {
                viewModel.cancel()
                openLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Included(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkingDialog() {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        SectionCard {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text("Working…", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** #12 Create Backup: choose and confirm a password for the file. */
@Composable
private fun ChooseBackupPasswordDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    SecureScreen()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a password to encrypt the backup. You'll need it to restore. We can't recover it if you forget it.")
                ErrorBanner(error)
                PasswordField(password, { password = it }, label = "Backup password")
                PasswordField(confirm, { confirm = it }, label = "Confirm password")
                Text("At least 8 characters, with a letter and a number.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                error = when {
                    !InputValidation.passwordRules(password).allMet -> "Use at least 8 characters, with a letter and a number."
                    password != confirm -> "The passwords don't match."
                    else -> null
                }
                if (error == null) onConfirm(password.toCharArray())
            }) { Text("Choose where to save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** #15 Import Backup: enter the file's password. */
@Composable
private fun EnterBackupPasswordDialog(error: String?, onDismiss: () -> Unit, onUnlock: (CharArray) -> Unit) {
    SecureScreen()
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
        title = { Text("Unlock backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the password you chose when you created this backup.")
                ErrorBanner(error)
                PasswordField(password, { password = it }, label = "Backup password", onDone = { onUnlock(password.toCharArray()) })
            }
        },
        confirmButton = { Button(onClick = { onUnlock(password.toCharArray()) }, enabled = password.isNotEmpty()) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** #16 Review Backup: date, device and contents before restoring. */
@Composable
private fun ReviewBackupDialog(review: BackupStep.Review, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = review.contents
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewRow("Created", TimeFormat.dateTime(context, c.createdAt))
                ReviewRow("Device", c.deviceName)
                ReviewRow("History", "${c.events.size} entries")
                ReviewRow("Preferences", "Notifications ${onOff(c.notificationsEnabled)}, History ${onOff(c.historyEnabled)}, Weekly review ${onOff(c.periodicReview)}")
                HorizontalDivider()
                Text(
                    "History is merged with what's already on this phone; nothing is deleted. Android's permissions won't change.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun onOff(b: Boolean) = if (b) "on" else "off"

@Composable
private fun ReviewRow(label: String, value: String) {
    Row {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun ResultDialog(
    success: Boolean,
    title: String,
    message: String,
    onDone: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val risk = LocalRiskColors.current
    AlertDialog(
        onDismissRequest = onDone,
        icon = {
            Icon(
                if (success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (success) risk.low else risk.high,
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            if (onRetry != null) Button(onClick = onRetry) { Text("Try again") }
            else Button(onClick = onDone) { Text("Done") }
        },
        dismissButton = { if (onRetry != null) TextButton(onClick = onDone) { Text("Cancel") } },
    )
}
