package com.group8.permissionmanagerapp_uss

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.ui.auth.AuthFlow
import com.group8.permissionmanagerapp_uss.ui.auth.AuthGate
import com.group8.permissionmanagerapp_uss.ui.auth.AuthViewModel
import com.group8.permissionmanagerapp_uss.ui.devices.DeviceGate
import com.group8.permissionmanagerapp_uss.ui.devices.DeviceGateFlow
import com.group8.permissionmanagerapp_uss.ui.devices.DeviceGateViewModel
import com.group8.permissionmanagerapp_uss.ui.navigation.PermissionManagerRoot
import com.group8.permissionmanagerapp_uss.ui.theme.PermissionManagerAppUSSTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Incremented each time a notification asks us to open the Notifications screen. */
    private val openNotificationsRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tapjacking protection: ignore touches while another app draws over this window
        // (e.g. a fake overlay trying to trick the user into tapping "Apply" or "Remove").
        window.decorView.filterTouchesWhenObscured = true
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PermissionManagerAppUSSTheme {
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
                val gate by authViewModel.gate.collectAsStateWithLifecycle()
                when (val g = gate) {
                    // Only fully signed-in users (password + email code) reach the app.
                    is AuthGate.SignedIn -> {
                        // Then this phone must be an approved device on the account (refinements #2–#6).
                        val deviceViewModel: DeviceGateViewModel = viewModel(key = "device-gate-${g.email}", factory = DeviceGateViewModel.Factory)
                        val deviceGate by deviceViewModel.gate.collectAsStateWithLifecycle()
                        if (deviceGate == DeviceGate.Approved) {
                            PermissionManagerRoot(
                                userName = g.name,
                                userEmail = g.email,
                                onSignOut = authViewModel::signOut,
                                openNotificationsRequest = openNotificationsRequest.intValue,
                            )
                        } else {
                            DeviceGateFlow(deviceGate, deviceViewModel, onSignOut = authViewModel::signOut)
                        }
                    }
                    else -> AuthFlow(g, authViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Catch changes made while we were away (e.g. in Android Settings). History and the
        // Notifications screen update; no system notification since the user is here.
        val container = (application as PermissionManagerApp).container
        // Back in the app (via the banner or Back): stop watching and clear the "Tap to return" banner.
        container.returnWatcher.stop()
        lifecycleScope.launch {
            runCatching { container.permissionMonitor.check(postNotifications = false) }
            container.appsRepository.refresh()
        }
    }

    private fun handleIntent(intent: Intent?) {
        // Only a boolean flag is read from the intent; nothing from it is trusted or forwarded.
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotificationsRequest.intValue++
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    }
}
