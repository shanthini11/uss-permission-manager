package com.group8.permissionmanagerapp_uss.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.model.Sensitivity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Hourly background check for permission changes; posts an alert if any are found. */
class PermissionCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PermissionManagerApp).container
        return try {
            container.permissionMonitor.check(postNotifications = true)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/** Weekly "time to review your permissions" reminder (lo-fi Settings > Periodic Review). */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PermissionManagerApp).container
        val prefs = container.preferences.preferences.first()
        if (!prefs.periodicReview) return Result.success()

        val apps = container.devicePermissions.loadApps(includeSystemApps = false)
        val critical = apps.count { app ->
            !app.isSystemApp && app.grantedPermissions.any { it.category.sensitivity == Sensitivity.CRITICAL }
        }
        container.history.addReviewReminder(isDemo = false)
        if (prefs.notificationsEnabled) container.notifier.notifyReview(critical)
        return Result.success()
    }
}

object WorkScheduler {
    private const val CHECK_WORK = "permission_check"
    private const val REVIEW_WORK = "weekly_review"

    fun schedule(context: Context, periodicReview: Boolean) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            CHECK_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PermissionCheckWorker>(1, TimeUnit.HOURS).build(),
        )
        if (periodicReview) {
            wm.enqueueUniquePeriodicWork(
                REVIEW_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReviewReminderWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(7, TimeUnit.DAYS)
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(REVIEW_WORK)
        }
    }
}
