package com.group8.permissionmanagerapp_uss.work

import android.content.Context
import android.content.Intent
import android.os.Build
import com.group8.permissionmanagerapp_uss.MainActivity
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.PermissionChoice
import com.group8.permissionmanagerapp_uss.data.repository.PermissionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * While the user is in Android Settings making a change we guided them to, checks once a second
 * whether it has happened. When it has, shows a one-tap "Tap to return" banner.
 *
 * Android 10+ blocks apps from bringing themselves to the front while another app is in use
 * (protection against apps hijacking the screen), so on those versions the banner is the fastest
 * legitimate way back. On Android 9 and below we return automatically.
 */
class ReturnWatcher(
    private val context: Context,
    private val device: PermissionRepository,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** Watch for one permission change. */
    fun watch(packageName: String, category: PermissionCategory, choice: PermissionChoice) {
        start(packageName) { app ->
            val access = app.accessFor(category)?.access ?: return@start null
            if (choice.matches(access)) "${app.label}: ${category.title} is now \"${choice.label}\"" else null
        }
    }

    /** Watch for Revoke All: done when none of [categories] are still allowed. */
    fun watchRevokeAll(packageName: String, categories: List<PermissionCategory>) {
        start(packageName) { app ->
            val stillAllowed = categories.count { app.accessFor(it)?.access?.isGranted == true }
            if (stillAllowed == 0) "${app.label}: all permissions revoked" else null
        }
    }

    /** Called when the user is back in our app (by any route): stop watching, remove the banner. */
    fun stop() {
        job?.cancel()
        job = null
        notifier.cancelReturnPrompt()
    }

    private fun start(packageName: String, doneMessage: (InstalledApp) -> String?) {
        job?.cancel()
        job = scope.launch {
            val deadline = System.currentTimeMillis() + WATCH_MS
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_MS)
                val app = runCatching { device.loadApp(packageName) }.getOrNull() ?: continue
                val message = doneMessage(app) ?: continue
                bringBack(message)
                return@launch
            }
        }
    }

    private fun bringBack(message: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Allowed on Android 9 and below: CLEAR_TOP removes the Settings pages above our screen.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } else {
            notifier.notifyReturnPrompt(message)
        }
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val WATCH_MS = 3 * 60 * 1_000L
    }
}
