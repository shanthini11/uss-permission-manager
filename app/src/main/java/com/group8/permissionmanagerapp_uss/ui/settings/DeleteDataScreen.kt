package com.group8.permissionmanagerapp_uss.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.history.HistoryRepository
import com.group8.permissionmanagerapp_uss.data.repository.DemoPermissionRepository
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import com.group8.permissionmanagerapp_uss.ui.auth.rememberIdentityGuard
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Refinements #19–#22: Delete Local Data -> Confirm -> Deleted / Error. */
enum class DeleteStep { IDLE, CONFIRM, WORKING, DELETED, ERROR }

class DeleteDataViewModel(
    private val history: HistoryRepository,
    private val preferences: UserPreferencesRepository,
    private val demo: DemoPermissionRepository,
    private val apps: AppsRepository,
) : ViewModel() {
    private val _step = MutableStateFlow(DeleteStep.IDLE)
    val step = _step.asStateFlow()

    fun askConfirm() {
        _step.value = DeleteStep.CONFIRM
    }

    fun delete() {
        _step.value = DeleteStep.WORKING
        viewModelScope.launch {
            _step.value = try {
                history.clearAll()
                preferences.clear()
                demo.clearChanges()
                apps.refresh()
                DeleteStep.DELETED
            } catch (_: Exception) {
                DeleteStep.ERROR
            }
        }
    }

    fun reset() {
        _step.value = DeleteStep.IDLE
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as PermissionManagerApp).container
                DeleteDataViewModel(c.history, c.preferences, c.demoPermissions, c.appsRepository)
            }
        }
    }
}

/** Lo-fi "Delete Local Data" (refinement #19). */
@Composable
fun DeleteDataScreen(
    modifier: Modifier = Modifier,
    viewModel: DeleteDataViewModel = viewModel(factory = DeleteDataViewModel.Factory),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val risk = LocalRiskColors.current
    val identity = rememberIdentityGuard()
    identity.Dialog()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Remove Permission Tracker's history and preferences from this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ListCard(
            "WHAT WILL BE DELETED",
            listOf("Permission history", "App preferences", "Permission review preferences", "Changes made in Demo mode"),
        )
        ListCard(
            "WHAT WILL NOT BE AFFECTED",
            listOf("Android's app permissions on this phone", "Your account (you stay signed in)", "Backup files you've saved"),
        )
        SectionCard {
            Text("This action cannot be undone.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
        }
        Button(
            onClick = { identity.run("delete local data", alwaysAsk = true) { viewModel.askConfirm() } },
            colors = ButtonDefaults.buttonColors(containerColor = risk.high),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Delete local data") }
    }

    when (step) {
        DeleteStep.IDLE, DeleteStep.WORKING -> Unit
        // #20 Confirm Deletion: warns about the consequences.
        DeleteStep.CONFIRM -> AlertDialog(
            onDismissRequest = viewModel::reset,
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = risk.high) },
            title = { Text("Delete local data?") },
            text = {
                Text(
                    "Your permission history and preferences will be permanently removed from this phone. " +
                        "If you want to keep them, export a backup first. Android's permissions won't change.",
                )
            },
            confirmButton = {
                Button(onClick = viewModel::delete, colors = ButtonDefaults.buttonColors(containerColor = risk.high)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::reset) { Text("Cancel") } },
        )
        // #21 Local Data Deleted.
        DeleteStep.DELETED -> ResultDialog(
            success = true,
            title = "Local data deleted",
            message = "Your history and preferences were removed. Android's app permissions on this phone have not been changed.",
            onDone = viewModel::reset,
        )
        // #22 Delete Error.
        DeleteStep.ERROR -> ResultDialog(
            success = false,
            title = "Data not deleted",
            message = "Something went wrong and your data wasn't deleted. Please try again.",
            onDone = viewModel::reset,
            onRetry = viewModel::delete,
        )
    }
}

@Composable
private fun ListCard(title: String, items: List<String>) {
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
