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
 * While this is in the composition, the window can't be screenshotted or recorded and is hidden
 * in the Recents preview. Used on screens showing passwords and verification codes.
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
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
