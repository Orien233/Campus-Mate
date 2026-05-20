package com.example.campusmate.domain.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.util.DateTimeUtils

/** Restores future task reminders after device boot when reminders are enabled. */
class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SettingsRepository(context).isReminderEnabled()) return

        val scheduler = AlarmReminderScheduler(context)
        val now = DateTimeUtils.nowMillis()
        TaskRepository(context).getAllTasks()
            .filter { task ->
                task.status == StudyTask.STATUS_TODO &&
                    !task.isDeleted &&
                    task.remindAt != null &&
                    task.remindAt > now
            }
            .forEach { scheduler.scheduleTaskReminder(it) }
    }
}
