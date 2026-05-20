package com.example.campusmate.domain.reminder

import com.example.campusmate.data.model.StudyTask

/** Abstraction for scheduling and cancelling task reminder alarms. */
interface ReminderScheduler {
    fun scheduleTaskReminder(task: StudyTask): ReminderScheduleResult

    fun cancelTaskReminder(taskId: Long)
}

data class ReminderScheduleResult(
    val scheduled: Boolean,
    val usedExactAlarm: Boolean,
    val message: String? = null
)
