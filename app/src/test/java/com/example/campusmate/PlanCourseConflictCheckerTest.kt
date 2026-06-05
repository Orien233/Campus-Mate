package com.example.campusmate

import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.domain.plan.PlanCourseConflictChecker
import com.example.campusmate.domain.plan.StudyPlanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCourseConflictCheckerTest {
    @Test
    fun findConflicts_reportsPlanOverlappingCourseTime() {
        val course = Course(
            id = 1L,
            name = "高等数学",
            weekday = 2,
            startSection = 1,
            endSection = 2
        )
        val plan = StudyPlan(
            title = "复习作业",
            planDate = "2026-06-02",
            plannedMinutes = 60,
            startTime = "08:30",
            endTime = "09:30"
        )

        val conflicts = PlanCourseConflictChecker.findConflicts(
            plans = listOf(plan),
            context = context(course)
        )

        assertEquals(1, conflicts.size)
        assertEquals("复习作业", conflicts.first().planTitle)
        assertEquals("高等数学", conflicts.first().courseName)
    }

    @Test
    fun findConflicts_ignoresPlanOutsideCourseTime() {
        val course = Course(
            id = 1L,
            name = "高等数学",
            weekday = 2,
            startSection = 1,
            endSection = 2
        )
        val plan = StudyPlan(
            title = "晚间复习",
            planDate = "2026-06-02",
            plannedMinutes = 60,
            startTime = "19:00",
            endTime = "20:00"
        )

        val conflicts = PlanCourseConflictChecker.findConflicts(
            plans = listOf(plan),
            context = context(course)
        )

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun findConflicts_ignoresMatchingCourseLearningPlanDuringCourseTime() {
        val course = Course(
            id = 1L,
            name = "高等数学",
            weekday = 2,
            startSection = 1,
            endSection = 2
        )
        val plan = StudyPlan(
            title = "完成课程学习：高等数学",
            planDate = "2026-06-02",
            plannedMinutes = 95,
            startTime = "08:00",
            endTime = "09:35"
        )

        val conflicts = PlanCourseConflictChecker.findConflicts(
            plans = listOf(plan),
            context = context(course)
        )

        assertTrue(conflicts.isEmpty())
    }

    private fun context(course: Course): StudyPlanContext {
        return StudyPlanContext(
            date = "2026-06-02",
            weekday = 2,
            weekdayName = "周二",
            dailyGoalMinutes = 60,
            courses = listOf(course),
            tasks = emptyList(),
            weather = null,
            recentStudyRecords = emptyList(),
            existingPlans = emptyList(),
            coursesById = mapOf(course.id to course),
            courseTimeRanges = mapOf(course.id to "08:00-09:35"),
            planEarliestTime = "08:00",
            planLatestTime = "22:00",
            generationStartTime = "08:00"
        )
    }
}
