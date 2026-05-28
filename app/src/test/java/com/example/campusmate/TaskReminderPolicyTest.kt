package com.example.campusmate

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.reminder.TaskReminderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReminderPolicyTest {
    @Test
    fun completedToPendingWithFutureReminder_shouldSchedule() {
        val task = task(status = StudyTask.STATUS_TODO, remindAt = FUTURE)

        assertTrue(
            TaskReminderPolicy.shouldScheduleWhenReopened(
                previousStatus = StudyTask.STATUS_DONE,
                reopenedTask = task,
                remindersEnabled = true,
                nowMillis = NOW
            )
        )
    }

    @Test
    fun completedToPendingWithPastReminder_shouldNotSchedule() {
        val task = task(status = StudyTask.STATUS_TODO, remindAt = PAST)

        assertFalse(
            TaskReminderPolicy.shouldScheduleWhenReopened(
                previousStatus = StudyTask.STATUS_DONE,
                reopenedTask = task,
                remindersEnabled = true,
                nowMillis = NOW
            )
        )
    }

    @Test
    fun completedToPendingWithoutReminder_shouldNotSchedule() {
        val task = task(status = StudyTask.STATUS_TODO, remindAt = null)

        assertFalse(
            TaskReminderPolicy.shouldScheduleWhenReopened(
                previousStatus = StudyTask.STATUS_DONE,
                reopenedTask = task,
                remindersEnabled = true,
                nowMillis = NOW
            )
        )
    }

    @Test
    fun completedToPendingWithReminderDisabled_shouldNotSchedule() {
        val task = task(status = StudyTask.STATUS_TODO, remindAt = FUTURE)

        assertFalse(
            TaskReminderPolicy.shouldScheduleWhenReopened(
                previousStatus = StudyTask.STATUS_DONE,
                reopenedTask = task,
                remindersEnabled = false,
                nowMillis = NOW
            )
        )
    }

    @Test
    fun pendingToCompleted_shouldCancel() {
        assertTrue(
            TaskReminderPolicy.shouldCancelWhenCompleted(
                previousStatus = StudyTask.STATUS_TODO,
                updatedStatus = StudyTask.STATUS_DONE
            )
        )
    }

    private fun task(status: Int, remindAt: Long?): StudyTask {
        return StudyTask(
            id = 7L,
            title = "Reminder policy task",
            remindAt = remindAt,
            status = status
        )
    }

    companion object {
        private const val NOW = 1_000L
        private const val PAST = 500L
        private const val FUTURE = 2_000L
    }
}
