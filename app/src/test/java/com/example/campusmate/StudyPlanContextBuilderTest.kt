package com.example.campusmate

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.plan.StudyPlanContext
import com.example.campusmate.domain.plan.StudyPlanContextBuilder
import com.example.campusmate.domain.weather.WeatherResult
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanContextBuilderTest {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    @Test
    fun weekdayForDate_usesRequestedDate() {
        assertEquals(4, StudyPlanContextBuilder.weekdayForDate("2026-06-04"))
        assertEquals(1, StudyPlanContextBuilder.weekdayForDate("2026-06-08"))
    }

    @Test
    fun generationStartTimeForDate_blocksPastDatesAndCurrentPastTime() {
        assertEquals(
            "22:00",
            StudyPlanContext.generationStartTimeForDate(
                date = "2026-06-04",
                today = "2026-06-05",
                earliest = "08:00",
                latest = "22:00",
                nowText = "15:30"
            )
        )
        assertEquals(
            "15:30",
            StudyPlanContext.generationStartTimeForDate(
                date = "2026-06-05",
                today = "2026-06-05",
                earliest = "08:00",
                latest = "22:00",
                nowText = "15:30"
            )
        )
        assertEquals(
            "08:00",
            StudyPlanContext.generationStartTimeForDate(
                date = "2026-06-06",
                today = "2026-06-05",
                earliest = "08:00",
                latest = "22:00",
                nowText = "15:30"
            )
        )
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

    @Test
    fun toPromptText_marksStaleWeatherAsHistoricalReference() {
        val context = StudyPlanContext(
            date = "2026-06-08",
            weekday = 1,
            weekdayName = "周一",
            dailyGoalMinutes = 60,
            courses = emptyList(),
            tasks = emptyList(),
            weather = WeatherResult(
                city = "北京",
                weatherText = "晴",
                temperature = "26°C",
                humidity = "40%",
                wind = "东风 2 级",
                source = "test",
                updatedAt = System.currentTimeMillis() - WeatherRepository.CACHE_MAX_AGE_MILLIS - 1L
            ),
            recentStudyRecords = emptyList(),
            existingPlans = emptyList(),
            coursesById = emptyMap(),
            courseTimeRanges = emptyMap(),
            planEarliestTime = "08:00",
            planLatestTime = "22:00",
            generationStartTime = "08:00"
        )

        assertTrue(context.toPromptText().contains("缓存已过期，只能作为历史参考"))
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
