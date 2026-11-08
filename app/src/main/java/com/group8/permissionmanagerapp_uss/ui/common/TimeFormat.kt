package com.group8.permissionmanagerapp_uss.ui.common

import android.content.Context
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Date helpers that work on minSdk 24 (no java.time). */
object TimeFormat {
    fun time(context: Context, t: Long): String =
        DateUtils.formatDateTime(context, t, DateUtils.FORMAT_SHOW_TIME)

    fun dateTime(context: Context, t: Long): String =
        DateUtils.formatDateTime(
            context, t,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )

    /** Midnight at the start of the day containing [t]. */
    fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayGroup(t: Long, now: Long): String {
        val today = startOfDay(now)
        return when {
            t >= today -> "Today"
            t >= today - DAY -> "Yesterday"
            t >= today - 6 * DAY -> "Earlier this week"
            else -> "Older"
        }
    }

    fun weekdayShort(t: Long): String = SimpleDateFormat("EEE", Locale.getDefault()).format(t)

    const val DAY = 24L * 60 * 60 * 1000
}
