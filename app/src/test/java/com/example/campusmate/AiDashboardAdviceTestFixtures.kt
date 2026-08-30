package com.example.campusmate

import com.example.campusmate.domain.ai.context.AiBusyWindow
import com.example.campusmate.domain.ai.context.AiBusyWindowType
import com.example.campusmate.domain.ai.context.AiContextOmissions
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.domain.ai.context.AiContextWarning
import com.example.campusmate.domain.ai.context.AiContextWarningCode
import com.example.campusmate.domain.ai.context.AiCourseFact
import com.example.campusmate.domain.ai.context.AiDailyStudyFact
import com.example.campusmate.domain.ai.context.AiDayContext
import com.example.campusmate.domain.ai.context.AiLearningProgress
import com.example.campusmate.domain.ai.context.AiLearningTrend
import com.example.campusmate.domain.ai.context.AiOccupiedTimeRange
import com.example.campusmate.domain.ai.context.AiPlanFact
import com.example.campusmate.domain.ai.context.AiPlanningSettings
import com.example.campusmate.domain.ai.context.AiTaskFact
import com.example.campusmate.domain.ai.context.AiWeatherFact
import com.example.campusmate.domain.ai.context.AiWeatherFreshness
import java.time.Instant

internal fun dashboardAdviceSnapshot(
    generatedAt: Long = Instant.parse("2026-06-08T00:00:00Z").toEpochMilli(),
    weatherFresh: Boolean = true,
    weatherUpdatedAt: Long = generatedAt - 10 * 60 * 1000L
): AiContextSnapshot {
    val firstDay = AiDayContext(
        date = "2026-06-08",
        weekday = 1,
        weekdayName = "周一",
        courses = listOf(
            AiCourseFact(
                localRef = "course:1",
                name = "数据结构",
                teacher = "张老师",
                classroom = "逸夫楼 101",
                startSection = 1,
                endSection = 2,
                timeRange = "08:00-10:00",
                startWeek = 1,
                endWeek = 16,
                weekType = "all"
            )
        ),
        existingPlans = listOf(
            AiPlanFact(
                localRef = "plan:2",
                title = "复习",
                startTime = "14:00",
                endTime = "15:00",
                plannedMinutes = 60,
                actualMinutes = 0,
                status = "pending"
            )
        ),
        busyWindows = listOf(
            AiBusyWindow(
                startTime = "08:00",
                endTime = "10:00",
                type = AiBusyWindowType.COURSE,
                label = "数据结构",
                localRef = "course:1"
            )
        ),
        occupiedTimeRanges = listOf(
            AiOccupiedTimeRange("08:00", "10:00"),
            AiOccupiedTimeRange("14:00", "15:00")
        )
    )
    val emptyDays = listOf(
        AiDayContext("2026-06-09", 2, "周二", emptyList(), emptyList(), emptyList(), emptyList()),
        AiDayContext("2026-06-10", 3, "周三", emptyList(), emptyList(), emptyList(), emptyList())
    )
    return AiContextSnapshot(
        generatedAt = generatedAt,
        zoneId = "Asia/Shanghai",
        purpose = AiContextPurpose.DASHBOARD_ADVICE,
        rangeStart = "2026-06-08",
        rangeEnd = "2026-06-10",
        settings = AiPlanningSettings(
            dailyGoalMinutes = 120,
            earliestPlanTime = "08:00",
            latestPlanTime = "22:00",
            weatherCity = "北京"
        ),
        days = listOf(firstDay) + emptyDays,
        pendingTasks = listOf(
            AiTaskFact(
                localRef = "task:3",
                courseRef = "course:1",
                courseName = "数据结构",
                title = "提交实验",
                description = "完成报告",
                type = "assignment",
                priority = "high",
                dueAt = generatedAt + 6 * 60 * 60 * 1000L,
                remindAt = null,
                overdue = false
            )
        ),
        learningProgress = AiLearningProgress(
            days = listOf(AiDailyStudyFact("2026-06-07", 90, 2)),
            totalMinutes = 90,
            averageMinutesPerDay = 90,
            activeDays = 1,
            currentStreakDays = 1,
            goalHitDays = 0,
            trend = AiLearningTrend.INSUFFICIENT_DATA
        ),
        weather = AiWeatherFact(
            city = "北京",
            weatherText = "晴",
            temperature = "25°C",
            humidity = "40%",
            wind = "东风 2 级",
            source = "test",
            updatedAt = weatherUpdatedAt,
            ageMillis = (generatedAt - weatherUpdatedAt).coerceAtLeast(0L),
            freshness = if (weatherFresh) AiWeatherFreshness.FRESH else AiWeatherFreshness.STALE,
            usableForRealtimeAdvice = weatherFresh
        ),
        warnings = listOf(
            AiContextWarning(
                AiContextWarningCode.COURSE_WEEK_UNRESOLVED,
                "课程教学周尚未解析"
            )
        ),
        omitted = AiContextOmissions()
    )
}
