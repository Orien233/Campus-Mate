package com.example.campusmate

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.plan.StudyPlanContextBuilder
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanContextBuilderTest {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    @Test
    fun weekdayForDate_usesRequestedDate() {
        assertEquals(4, StudyPlanContextBuilder.weekdayForDate("2026-06-04"))
        assertEquals(1, StudyPlanContextBuilder.weekdayForDate("2026-06-08"))
    }

    @Test
    fun sortTasksForDate_prioritizesDueDateCourseAndPriority() {
        val courseTask = task(
            title = "课程预习",
            priority = StudyTask.PRIORITY_NORMAL,
            dueAt = millis("2026-06-04 20:00"),
            courseId = 10L
        )
        val highPriorityNearDue = task(
            title = "实验报告",
            priority = StudyTask.PRIORITY_HIGH,
            dueAt = millis("2026-06-05 12:00")
        )
        val noDue = task(
            title = "长期阅读",
            priority = StudyTask.PRIORITY_HIGH,
            dueAt = null
        )
        val farFuture = task(
            title = "远期任务",
            priority = StudyTask.PRIORITY_NORMAL,
            dueAt = millis("2026-07-01 12:00")
        )

        val sorted = StudyPlanContextBuilder.sortTasksForDate(
            tasks = listOf(noDue, farFuture, highPriorityNearDue, courseTask),
            date = "2026-06-04",
            courseIds = setOf(10L)
        )

        assertEquals(listOf("课程预习", "实验报告", "长期阅读"), sorted.map { it.title })
    }

    private fun task(
        title: String,
        priority: Int,
        dueAt: Long?,
        courseId: Long? = null
    ): StudyTask {
        return StudyTask(
            title = title,
            priority = priority,
            dueAt = dueAt,
            courseId = courseId,
            status = StudyTask.STATUS_TODO,
            createdAt = 1L
        )
    }

    private fun millis(value: String): Long = dateFormat.parse(value)!!.time
}
