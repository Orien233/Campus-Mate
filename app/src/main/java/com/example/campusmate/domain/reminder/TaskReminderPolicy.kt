package com.example.campusmate.domain.reminder

import com.example.campusmate.data.model.StudyTask

/** Pure decision rules for reminder changes after task status transitions. */
object TaskReminderPolicy {
    fun shouldScheduleWhenReopened(
        previousStatus: Int,
        reopenedTask: StudyTask,
        remindersEnabled: Boolean,
        nowMillis: Long
    ): Boolean {
        if (previousStatus != StudyTask.STATUS_DONE) return false
        if (reopenedTask.status != StudyTask.STATUS_TODO || reopenedTask.isDeleted) return false
        if (!remindersEnabled) return false
        return reopenedTask.remindAt?.let { it > nowMillis } == true
    }

    fun shouldCancelWhenCompleted(previousStatus: Int, updatedStatus: Int): Boolean {
        return previousStatus != StudyTask.STATUS_DONE && updatedStatus == StudyTask.STATUS_DONE
    }
}
