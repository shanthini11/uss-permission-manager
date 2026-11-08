package com.group8.permissionmanagerapp_uss.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.group8.permissionmanagerapp_uss.MainActivity
import com.group8.permissionmanagerapp_uss.R
import com.group8.permissionmanagerapp_uss.data.history.PermissionEvent

/**
 * Posts system notifications for permission changes and weekly reviews.
 *
 * Privacy: notifications are VISIBILITY_PRIVATE with a generic public version, so app names
 * are not shown on the lock screen to someone who picks up the phone.
 */
class Notifier(private val context: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHANGES, "Permission changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when an app's permissions change"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, "Weekly review", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A weekly reminder to review app permissions"
            }
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyChanges(events: List<PermissionEvent>) {
        val changes = events.filter { !it.isReminder && it.category != null }
        if (changes.isEmpty() || !canPost()) return

        val title = if (changes.size == 1) "Permission changed" else "${changes.size} permission changes"
        val lines = changes.take(5).map { describe(it) }
        val text = lines.first() + if (changes.size > 1) " and ${changes.size - 1} more" else ""

        post(
            id = ID_CHANGES,
            channel = CHANNEL_CHANGES,
            title = title,
            text = text,
            style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } },
            publicText = "Open Permission Manager to review",
        )
    }

    fun notifyReview(criticalApps: Int) {
        if (!canPost()) return
        post(
            id = ID_REVIEW,
            channel = CHANNEL_REVIEW,
            title = "Time for your weekly permission review",
            text = if (criticalApps > 0) "$criticalApps apps can use your camera, microphone or location. Take a minute to check them."
            else "Take a minute to check which apps can access your data.",
            style = null,
            publicText = "Weekly permission review",
        )
    }

    private fun post(
        id: Int,
        channel: String,
        title: String,
        text: String,
        style: NotificationCompat.Style?,
        publicText: String,
    ) {
        // Explicit intent to our own activity; immutable so other apps can't modify it.
        val pending = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Permission Manager")
            .setContentText(publicText)
            .build()

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked between the check and the call; nothing to do.
        }
    }

    companion object {
        const val CHANNEL_CHANGES = "permission_changes"
        const val CHANNEL_REVIEW = "weekly_review"
        private const val ID_CHANGES = 1001
        private const val ID_REVIEW = 1002

        fun describe(event: PermissionEvent): String {
            val category = event.category?.title ?: return event.appLabel
            val to = event.to ?: return "${event.appLabel}: $category changed"
            return when {
                to.isGranted && event.from?.isGranted != true -> "${event.appLabel} can now use $category (${to.label.lowercase()})"
                !to.isGranted -> "${event.appLabel} can no longer use $category"
                else -> "${event.appLabel}: $category is now \"${to.label.lowercase()}\""
            }
        }
    }
}
