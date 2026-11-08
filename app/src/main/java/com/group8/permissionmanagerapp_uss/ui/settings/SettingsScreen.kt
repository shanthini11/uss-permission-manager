package com.group8.permissionmanagerapp_uss.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferences
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val prefs: UserPreferencesRepository) : ViewModel() {
    val preferences: StateFlow<UserPreferences> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setDemoMode(enabled: Boolean) = viewModelScope.launch { prefs.setDemoMode(enabled) }
    fun setShowSystemApps(enabled: Boolean) = viewModelScope.launch { prefs.setShowSystemApps(enabled) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    fun setHistory(enabled: Boolean) = viewModelScope.launch { prefs.setHistoryEnabled(enabled) }
    fun setPeriodicReview(enabled: Boolean) = viewModelScope.launch { prefs.setPeriodicReview(enabled) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                SettingsViewModel(app.container.preferences)
            }
        }
    }
}

/**
 * Settings. Day 1 has the app-data options; Notifications, History, Periodic Review,
 * Backup & Restore, Privacy Information, About and Delete Local Data arrive on Days 3–5.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Privacy & permission preferences",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow(
            title = "Permission Notifications",
            subtitle = "Alert me when an app's permissions change, even when this app is closed.",
            checked = prefs.notificationsEnabled,
            onCheckedChange = viewModel::setNotifications,
        )
        ToggleRow(
            title = "Permission History",
            subtitle = "Keep an encrypted record of permission changes on this device for the last 30 days.",
            checked = prefs.historyEnabled,
            onCheckedChange = viewModel::setHistory,
        )
        ToggleRow(
            title = "Periodic Review",
            subtitle = "A weekly reminder to check which apps can access your data.",
            checked = prefs.periodicReview,
            onCheckedChange = viewModel::setPeriodicReview,
        )
        ToggleRow(
            title = "Demo mode",
            subtitle = "Show sample apps instead of the apps on this phone. Useful for trying the " +
                "app on an emulator. Nothing on your phone changes.",
            checked = prefs.demoMode,
            onCheckedChange = viewModel::setDemoMode,
        )
        ToggleRow(
            title = "Show system apps",
            subtitle = "Include built-in Android components that don't appear in your app drawer.",
            checked = prefs.showSystemApps,
            onCheckedChange = viewModel::setShowSystemApps,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SectionCard {
        Row(
            Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$title · ${if (checked) "ON" else "OFF"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            // Row handles the click so the whole card is a large touch target.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
