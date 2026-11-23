package com.group8.permissionmanagerapp_uss.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.auth.AuthException
import com.group8.permissionmanagerapp_uss.data.auth.AuthRepository
import com.group8.permissionmanagerapp_uss.data.auth.InputValidation
import com.group8.permissionmanagerapp_uss.ui.common.SecureScreen
import com.group8.permissionmanagerapp_uss.ui.common.TimeFormat
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val events: List<AuthRepository.SecurityEvent> = emptyList(),
)

class AccountViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state = _state.asStateFlow()

    fun loadEvents() {
        viewModelScope.launch { _state.value = _state.value.copy(events = auth.securityEvents()) }
    }

    fun changePassword(current: String, new: String, confirm: String, email: String, onDone: () -> Unit) {
        if (!InputValidation.passwordRules(new, email).allMet) {
            _state.value = _state.value.copy(error = "Your new password doesn't meet the rules.")
            return
        }
        if (new != confirm) {
            _state.value = _state.value.copy(error = "The passwords don't match.")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                auth.changePassword(current, new)
                _state.value = _state.value.copy(busy = false, message = "Password changed")
                onDone()
            } catch (e: AuthException) {
                _state.value = _state.value.copy(busy = false, error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { AccountViewModel((this[APPLICATION_KEY] as PermissionManagerApp).container.auth) }
        }
    }
}

/** Lo-fi "Profile Page": account details, change password, security activity, log out. */
@Composable
fun ProfileScreen(
    name: String,
    email: String,
    snackbarHostState: SnackbarHostState,
    onPrivacySettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var changing by rememberSaveable { mutableStateOf(false) }
    var showActivity by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(80.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.height(12.dp))
                Text(name.ifBlank { "Your account" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Stored only on this phone · protected by your password and phone unlock",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SectionCard {
            Column {
                InfoRow("Full name", name.ifBlank { "Not set" })
                HorizontalDivider()
                InfoRow("Email address", email)
            }
        }
        SectionCard {
            Column {
                NavRow(Icons.Outlined.Password, "Change password", { viewModel.clearError(); changing = true })
                HorizontalDivider()
                NavRow(Icons.Outlined.History, "Security activity", { viewModel.loadEvents(); showActivity = true })
                HorizontalDivider()
                NavRow(Icons.Outlined.Lock, "Privacy settings", onPrivacySettings)
            }
        }
        SectionCard { NavRow(Icons.AutoMirrored.Outlined.Logout, "Log out", { confirmSignOut = true }, showChevron = false) }
    }

    if (changing) {
        ChangePasswordDialog(state, email, viewModel, onDismiss = { changing = false })
    }

    if (showActivity) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showActivity = false },
            icon = { Icon(Icons.Outlined.History, contentDescription = null) },
            title = { Text("Security activity") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Sign-ins, unlocks, wrong passwords and lockouts on this phone. Stored encrypted.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.events.isEmpty()) Text("No activity yet.")
                    state.events.take(50).forEach { e ->
                        Row {
                            Text(e.type, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(TimeFormat.dateTime(context, e.time), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showActivity = false }) { Text("Close") } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Log out?") },
            text = { Text("You'll need your password and your phone's unlock to sign in again. Your permission history stays on this phone.") },
            confirmButton = {
                Button(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChangePasswordDialog(state: AccountUiState, email: String, viewModel: AccountViewModel, onDismiss: () -> Unit) {
    SecureScreen()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Password, contentDescription = null) },
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorBanner(state.error)
                PasswordField(current, { current = it }, label = "Current password")
                PasswordField(new, { new = it }, label = "New password")
                PasswordField(confirm, { confirm = it }, label = "Confirm new password")
                Text(
                    "At least ${InputValidation.MIN_PASSWORD} characters with a letter and a number; not a common password.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.changePassword(current, new, confirm, email, onDismiss) }, enabled = !state.busy) {
                Text("Change")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f, fill = false),
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit, showChevron: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
