package com.group8.permissionmanagerapp_uss.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.group8.permissionmanagerapp_uss.ui.common.SecureScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VerifyState(val busy: Boolean = false, val error: String? = null)

class VerifyIdentityViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(VerifyState())
    val state = _state.asStateFlow()

    /** Skip the prompt if the password was confirmed in the last couple of minutes. */
    fun recentlyVerified() = auth.recentlyVerified()

    fun verify(password: String, onSuccess: () -> Unit) {
        if (password.isEmpty()) {
            _state.value = VerifyState(error = "Enter your password.")
            return
        }
        if (_state.value.busy) return
        _state.value = VerifyState(busy = true)
        viewModelScope.launch {
            try {
                auth.reauthenticate(password)
                _state.value = VerifyState()
                onSuccess()
            } catch (e: AuthException) {
                _state.value = VerifyState(error = e.message)
            }
        }
    }

    fun reset() {
        _state.value = VerifyState()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                VerifyIdentityViewModel((this[APPLICATION_KEY] as PermissionManagerApp).container.auth)
            }
        }
    }
}

/**
 * Refinement #10 "Verify Your Identity": password re-check before sensitive actions (UP9).
 * Firebase rate-limits wrong passwords on the server.
 */
@Composable
fun VerifyIdentityDialog(
    reason: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: VerifyIdentityViewModel = viewModel(factory = VerifyIdentityViewModel.Factory),
) {
    SecureScreen()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    val dismiss = {
        viewModel.reset()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text("Verify your identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("For your security, please enter your password before you $reason.")
                ErrorBanner(state.error)
                PasswordField(password, { password = it }, onDone = { viewModel.verify(password, onVerified) })
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.verify(password, onVerified) }, enabled = !state.busy) {
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Verify")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

/**
 * Asks for the password before an action.
 *
 * - Frequent, low-risk actions (a single permission change) skip the prompt if the password was
 *   confirmed in the last 2 minutes, so changing several permissions in a row stays quick.
 * - High-impact actions pass [alwaysAsk] = true and are verified every time: Revoke All, removing
 *   devices, deleting local data, and backup export/import.
 *
 * Usage: `val guard = rememberIdentityGuard(); guard.run("apply this change") { ... }` and call `guard.Dialog()`.
 */
class IdentityGuard internal constructor(private val viewModel: VerifyIdentityViewModel) {
    internal var pending by mutableStateOf<Pair<String, () -> Unit>?>(null)

    fun run(reason: String, alwaysAsk: Boolean = false, action: () -> Unit) {
        if (!alwaysAsk && viewModel.recentlyVerified()) action() else pending = reason to action
    }

    @Composable
    fun Dialog() {
        val (reason, action) = pending ?: return
        VerifyIdentityDialog(
            reason = reason,
            onVerified = {
                pending = null
                action()
            },
            onDismiss = { pending = null },
            viewModel = viewModel,
        )
    }
}

@Composable
fun rememberIdentityGuard(
    viewModel: VerifyIdentityViewModel = viewModel(factory = VerifyIdentityViewModel.Factory),
): IdentityGuard = remember(viewModel) { IdentityGuard(viewModel) }
