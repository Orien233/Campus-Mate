package com.example.campusmate.domain.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyTask

/** AlarmManager implementation for task reminders with exact-alarm fallback. */
class AlarmReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun scheduleTaskReminder(task: StudyTask): ReminderScheduleResult {
        val remindAt = task.remindAt
            ?: return ReminderScheduleResult(scheduled = false, usedExactAlarm = false, message = appContext.getString(R.string.reminder_no_time))
        if (task.id <= 0L || remindAt <= System.currentTimeMillis()) {
            return ReminderScheduleResult(scheduled = false, usedExactAlarm = false, message = appContext.getString(R.string.reminder_invalid_time))
        }

        val pendingIntent = buildPendingIntent(task.id)
        val canUseExact = canScheduleExactAlarms()
        if (canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pendingIntent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, remindAt, FALLBACK_WINDOW_MILLIS, pendingIntent)
        }
        return ReminderScheduleResult(
            scheduled = true,
            usedExactAlarm = canUseExact,
            message = if (canUseExact) null else appContext.getString(R.string.reminder_exact_alarm_unavailable)
        )
    }

    override fun cancelTaskReminder(taskId: Long) {
        if (taskId <= 0L) return
        alarmManager.cancel(buildPendingIntent(taskId))
    }

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun buildPendingIntent(taskId: Long): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TASK_REMINDER
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val FALLBACK_WINDOW_MILLIS = 5 * 60 * 1000L
    }
}
