package com.group8.permissionmanagerapp_uss.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.group8.permissionmanagerapp_uss.BuildConfig

/**
 * Release builds protect the whole app (MainActivity sets FLAG_SECURE once): permission lists reveal
 * which apps a person uses, so every screen is sensitive, not only password screens (peer-testing finding).
 */
const val SECURE_WHOLE_APP = true

/** Applies FLAG_SECURE to [activity] for its whole lifetime in release builds. */
fun secureWholeApp(activity: Activity) {
    if (SECURE_WHOLE_APP && !BuildConfig.DEBUG) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

/**
 * While this is in the composition, the window can't be screenshotted or recorded and is hidden
 * in the Recents preview. Marks screens showing passwords and verification codes; with
 * [SECURE_WHOLE_APP] the flag is already on and is left on when the screen closes.
 *
 * Debug builds skip it: Android Studio's emulator view and screen recording are screen capture too,
 * and would show a frozen frame. The release APK (what users and testers install) always applies it.
 */
@Composable
fun SecureScreen() {
    if (BuildConfig.DEBUG) return
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!SECURE_WHOLE_APP) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
