package com.example.campusmate.domain.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.ui.task.TaskDetailActivity
import com.example.campusmate.util.NotificationUtils

/** Receives task reminder alarms and shows notifications for active tasks only. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TASK_REMINDER) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId <= 0L) return

        val task = TaskRepository(context).getTaskById(taskId) ?: return
        if (task.status != StudyTask.STATUS_TODO || task.isDeleted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationUtils.areNotificationsEnabled(context)) return

        NotificationUtils.ensureTaskReminderChannel(context)
        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            taskId.toInt(),
            Intent(context, TaskDetailActivity::class.java).apply {
                putExtra(TaskDetailActivity.EXTRA_TASK_ID, taskId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val dueText = task.dueAt?.let { com.example.campusmate.util.DateTimeUtils.formatDateTime(it) }
            ?: context.getString(R.string.task_no_due_time)
        val notification = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_task)
            .setContentTitle(context.getString(R.string.notification_task_reminder_title))
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_task_reminder_body, task.title, dueText)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notifyTaskReminder(context, taskId, notification)
    }

    @SuppressLint("MissingPermission")
    private fun notifyTaskReminder(context: Context, taskId: Long, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(NotificationUtils.taskReminderNotificationId(taskId), notification)
    }

    companion object {
        const val ACTION_TASK_REMINDER = "com.example.campusmate.action.TASK_REMINDER"
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
