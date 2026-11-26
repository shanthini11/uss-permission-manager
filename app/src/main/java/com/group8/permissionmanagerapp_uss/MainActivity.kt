package com.group8.permissionmanagerapp_uss

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import com.group8.permissionmanagerapp_uss.ui.common.secureWholeApp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.ui.auth.AuthFlow
import com.group8.permissionmanagerapp_uss.ui.auth.AuthGate
import com.group8.permissionmanagerapp_uss.ui.auth.AuthViewModel
import com.group8.permissionmanagerapp_uss.data.auth.AuthRepository
import com.group8.permissionmanagerapp_uss.ui.navigation.PermissionManagerRoot
import com.group8.permissionmanagerapp_uss.ui.theme.PermissionManagerAppUSSTheme
import kotlinx.coroutines.launch

// FragmentActivity (a ComponentActivity) is required by BiometricPrompt for the phone-unlock step.
class MainActivity : FragmentActivity() {

    /** Incremented each time a notification asks us to open the Notifications screen. */
    private val openNotificationsRequest = mutableIntStateOf(0)

    private var authViewModel: AuthViewModel? = null

    /** When the app last went to the background (elapsed realtime, unaffected by clock changes). */
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tapjacking protection: ignore touches while another app draws over this window
        // (e.g. a fake overlay trying to trick the user into tapping "Apply" or "Remove").
        window.decorView.filterTouchesWhenObscured = true
        // Android 12+: hide other apps' overlay windows entirely while we're on screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)
        // No screenshots, screen recording or Recents preview of any screen (release builds).
        secureWholeApp(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PermissionManagerAppUSSTheme {
                // Surface sets the default text colour from the theme. Without it, screens drawn on a
                // plain background (sign-in, sign-up) fell back to black text, unreadable in dark mode.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
                    authViewModel = vm
                    val gate by vm.gate.collectAsStateWithLifecycle()
                    when (val g = gate) {
                        // Only users who passed password + phone unlock reach the app.
                        is AuthGate.SignedIn -> PermissionManagerRoot(
                            userName = g.name,
                            userEmail = g.email,
                            onSignOut = vm::signOut,
                            openNotificationsRequest = openNotificationsRequest.intValue,
                        )
                        else -> AuthFlow(g, vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Auto-lock: after 5 minutes in the background, ask for the phone unlock again.
        if (backgroundedAt != 0L &&
            SystemClock.elapsedRealtime() - backgroundedAt > AuthRepository.AUTO_LOCK_MS
        ) {
            authViewModel?.autoLock()
        }
        backgroundedAt = 0L
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
        // Reading any extra unpacks the whole bundle, so a malicious app could send a malformed one
        // to crash us on launch (local denial of service): treat any unpacking error as "no extra".
        val open = try {
            intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true
        } catch (_: RuntimeException) {
            false
        }
        if (open) openNotificationsRequest.intValue++
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    }
}
