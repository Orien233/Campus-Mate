package com.example.campusmate.domain.plan

import android.content.Context
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.util.DateTimeUtils

class StudyPlanGenerator(private val context: Context) {
    private val courseRepository = CourseRepository(context)
    private val taskRepository = TaskRepository(context)
    private val planRepository = StudyPlanRepository(context)

    private val sectionStartHours = mapOf(
        1 to 8, 2 to 8, 3 to 9, 4 to 10, 5 to 10, 6 to 11,
        7 to 11, 8 to 14, 9 to 14, 10 to 15, 11 to 16, 12 to 16
    )

    private val sectionDurationMinutes = mapOf(
        1 to 45, 2 to 45, 3 to 45, 4 to 45, 5 to 45, 6 to 45,
        7 to 45, 8 to 45, 9 to 45, 10 to 45, 11 to 45, 12 to 45
    )

    private val breakBetweenSections = 10

    data class PlanGenerationResult(
        val success: Boolean,
        val plans: List<StudyPlan> = emptyList(),
        val courseCount: Int = 0,
        val taskCount: Int = 0,
        val totalPlannedMinutes: Int = 0,
        val message: String = ""
    )

    fun generateDailyPlan(dateMillis: Long = System.currentTimeMillis()): PlanGenerationResult {
        val date = DateTimeUtils.formatDate(dateMillis)
        val weekday = calculateWeekday(dateMillis)

        if (planRepository.hasPlanForDate(date)) {
            return PlanGenerationResult(
                success = false,
                message = "当日计划已存在，如需重新生成请先删除现有计划"
            )
        }

        val courses = courseRepository.getCoursesByWeekday(weekday)
        val tasks = getTasksForDate(date)
        val plans = mutableListOf<StudyPlan>()

        var currentHour = 7
        var currentMinute = 30

        for (course in courses.sortedBy { it.startSection }) {
            val startTime = formatTime(currentHour, currentMinute)
            val courseMinutes = calculateCourseDuration(course)
            val endMinute = currentMinute + courseMinutes
            val (endHour, endMin) = adjustTime(currentHour, endMinute)

            plans.add(
                StudyPlan(
                    title = "上课: ${course.name}",
                    planDate = date,
                    plannedMinutes = courseMinutes,
                    startTime = startTime,
                    endTime = formatTime(endHour, endMin),
                    type = StudyPlan.TYPE_DAILY,
                    sourceType = StudyPlan.SOURCE_AUTO
                )
            )

            currentHour = endHour
            currentMinute = endMin + breakBetweenSections

            if (currentMinute >= 60) {
                currentHour += currentMinute / 60
                currentMinute = currentMinute % 60
            }
        }

        if (tasks.isNotEmpty()) {
            val taskMinutes = tasks.sumOf { estimateTaskDuration(it) }
            val endMinute = currentMinute + taskMinutes
            val (endHour, endMin) = adjustTime(currentHour, endMinute)

            plans.add(
                StudyPlan(
                    title = "自主学习: ${tasks.size}项待办任务",
                    planDate = date,
                    plannedMinutes = taskMinutes,
                    startTime = formatTime(currentHour, currentMinute),
                    endTime = formatTime(endHour, endMin),
                    type = StudyPlan.TYPE_DAILY,
                    sourceType = StudyPlan.SOURCE_AUTO
                )
            )
        }

        if (plans.isEmpty()) {
            val endMinute = currentMinute + 120
            val (endHour, endMin) = adjustTime(currentHour, endMinute)
            plans.add(
                StudyPlan(
                    title = "自主学习时间",
                    planDate = date,
                    plannedMinutes = 120,
                    startTime = formatTime(currentHour, currentMinute),
                    endTime = formatTime(endHour, endMin),
                    type = StudyPlan.TYPE_DAILY,
                    sourceType = StudyPlan.SOURCE_AUTO
                )
            )
        }

        val totalMinutes = plans.sumOf { it.plannedMinutes }
        planRepository.addPlans(plans)

        return PlanGenerationResult(
            success = true,
            plans = plans,
            courseCount = courses.size,
            taskCount = tasks.size,
            totalPlannedMinutes = totalMinutes,
            message = "已生成${plans.size}个计划项，涵盖${courses.size}节课和${tasks.size}项任务，共${totalMinutes}分钟"
        )
    }

    fun generateWeeklyPlan(): PlanGenerationResult {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        val weekday = DateTimeUtils.currentWeekday()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -(weekday - 1))

        val startDate = DateTimeUtils.formatDate(calendar.timeInMillis)
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 6)
        val endDate = DateTimeUtils.formatDate(calendar.timeInMillis)

        val allPlans = mutableListOf<StudyPlan>()
        var totalCourseCount = 0
        var totalTaskCount = 0

        calendar.timeInMillis = calendar.timeInMillis - (6 * 24 * 60 * 60 * 1000L)

        for (day in 0..6) {
            val dayDate = DateTimeUtils.formatDate(calendar.timeInMillis)
            val dayWeekday = calculateWeekday(calendar.timeInMillis)

            val courses = courseRepository.getCoursesByWeekday(dayWeekday)
            val tasks = getTasksForDate(dayDate)

            var currentHour = 7
            var currentMinute = 30

            for (course in courses.sortedBy { it.startSection }) {
                val startTime = formatTime(currentHour, currentMinute)
                val courseMinutes = calculateCourseDuration(course)
                val endMinute = currentMinute + courseMinutes
                val (endHour, endMin) = adjustTime(currentHour, endMinute)

                allPlans.add(
                    StudyPlan(
                        title = "上课: ${course.name}",
                        planDate = dayDate,
                        plannedMinutes = courseMinutes,
                        startTime = startTime,
                        endTime = formatTime(endHour, endMin),
                        type = StudyPlan.TYPE_WEEKLY,
                        sourceType = StudyPlan.SOURCE_AUTO
                    )
                )

                currentHour = endHour
                currentMinute = endMin + breakBetweenSections

                if (currentMinute >= 60) {
                    currentHour += currentMinute / 60
                    currentMinute = currentMinute % 60
                }
            }

            if (tasks.isNotEmpty()) {
                val taskMinutes = tasks.sumOf { estimateTaskDuration(it) }
                val endMinute = currentMinute + taskMinutes
                val (endHour, endMin) = adjustTime(currentHour, endMinute)

                allPlans.add(
                    StudyPlan(
                        title = "自主学习: ${tasks.size}项待办任务",
                        planDate = dayDate,
                        plannedMinutes = taskMinutes,
                        startTime = formatTime(currentHour, currentMinute),
                        endTime = formatTime(endHour, endMin),
                        type = StudyPlan.TYPE_WEEKLY,
                        sourceType = StudyPlan.SOURCE_AUTO
                    )
                )
            }

            totalCourseCount += courses.size
            totalTaskCount += tasks.size

            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        if (allPlans.isNotEmpty()) {
            planRepository.addPlans(allPlans)
        }

        val totalMinutes = allPlans.sumOf { it.plannedMinutes }

        return PlanGenerationResult(
            success = true,
            plans = allPlans,
            courseCount = totalCourseCount,
            taskCount = totalTaskCount,
            totalPlannedMinutes = totalMinutes,
            message = "已生成本周${allPlans.size}个计划项，涵盖${totalCourseCount}节课和${totalTaskCount}项任务，共${totalMinutes}分钟"
        )
    }

    private fun calculateWeekday(dateMillis: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = dateMillis
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        return when (dayOfWeek) {
            java.util.Calendar.SUNDAY -> 7
            else -> dayOfWeek - 1
        }
    }

    private fun calculateCourseDuration(course: Course): Int {
        val sections = course.endSection - course.startSection + 1
        val classDuration = 45
        val breakAfterClass = if (sections > 1) (sections - 1) * 5 else 0
        return sections * classDuration + breakAfterClass
    }

    private fun getTasksForDate(date: String): List<StudyTask> {
        val allTasks = taskRepository.getAllTasks()
        val dateMillis = try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }

        return allTasks.filter { task ->
            task.status == StudyTask.STATUS_TODO && !task.isDeleted &&
                (task.dueAt == null || task.dueAt >= dateMillis - (7 * 24 * 60 * 60 * 1000L))
        }
    }

    private fun estimateTaskDuration(task: StudyTask): Int {
        return when (task.type) {
            StudyTask.TYPE_HOMEWORK -> 60
            StudyTask.TYPE_EXPERIMENT -> 120
            StudyTask.TYPE_EXAM -> 180
            StudyTask.TYPE_REVIEW -> 90
            StudyTask.TYPE_PROJECT -> 120
            else -> 45
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
    }

    private fun adjustTime(hour: Int, totalMinutes: Int): Pair<Int, Int> {
        val adjustedHour = hour + totalMinutes / 60
        val adjustedMinute = totalMinutes % 60
        return Pair(adjustedHour.coerceAtMost(23), adjustedMinute)
    }
}
