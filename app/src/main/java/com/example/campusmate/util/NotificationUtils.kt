package com.example.campusmate.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.campusmate.R

/** Notification channel creation and shared notification identifiers. */
object NotificationUtils {
    const val CHANNEL_TASK_REMINDERS = "task_reminders"
    const val CHANNEL_FOCUS_SERVICE = "focus_service"

    fun ensureTaskReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_TASK_REMINDERS,
            context.getString(R.string.notification_channel_task_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_task_reminders_desc)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun ensureFocusServiceChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_FOCUS_SERVICE,
            context.getString(R.string.notification_channel_focus_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_focus_service_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun taskReminderNotificationId(taskId: Long): Int {
        return (TASK_REMINDER_NOTIFICATION_BASE + taskId).toInt()
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private const val TASK_REMINDER_NOTIFICATION_BASE = 10_000L
}
