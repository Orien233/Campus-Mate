package com.example.campusmate.domain.plan

import android.content.Context
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.util.DateTimeUtils
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class StudyPlanGenerator(private val context: Context) {
    private val planRepository = StudyPlanRepository(context)
    private val contextBuilder = StudyPlanContextBuilder(context)

    data class PlanGenerationResult(
        val success: Boolean,
        val plans: List<StudyPlan> = emptyList(),
        val courseCount: Int = 0,
        val taskCount: Int = 0,
        val totalPlannedMinutes: Int = 0,
        val message: String = ""
    )

    private data class TimeBlock(val start: Int, val end: Int)

    fun generateDailyPlan(dateMillis: Long = System.currentTimeMillis()): PlanGenerationResult {
        return generateDailyPlan(DateTimeUtils.formatDate(dateMillis))
    }

    fun generateDailyPlan(date: String): PlanGenerationResult {
        val planContext = contextBuilder.buildForDate(date)
        val planDate = planContext.date

        if (planRepository.hasPlanForDate(planDate)) {
            return PlanGenerationResult(
                success = false,
                message = "当日计划已存在，如需重新生成请先删除现有计划"
            )
        }

        val plans = generatePlansForContext(planContext, StudyPlan.TYPE_DAILY)
        if (plans.isNotEmpty()) {
            planRepository.addPlans(plans)
        }
        return buildResult(plans, planContext.courses.size, planContext.tasks.size, "已生成")
    }

    fun generateWeeklyPlan(): PlanGenerationResult {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -(DateTimeUtils.currentWeekday() - 1))

        val allPlans = mutableListOf<StudyPlan>()
        var totalCourseCount = 0
        var totalTaskCount = 0

        for (day in 0..6) {
            val dayContext = contextBuilder.buildForDate(DateTimeUtils.formatDate(calendar.timeInMillis))
            val dayPlans = generatePlansForContext(dayContext, StudyPlan.TYPE_WEEKLY)
            allPlans += dayPlans
            totalCourseCount += dayContext.courses.size
            totalTaskCount += dayContext.tasks.size
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        if (allPlans.isNotEmpty()) {
            planRepository.deletePlansOverlapping(allPlans)
            planRepository.addPlans(allPlans)
        }
        return buildResult(allPlans, totalCourseCount, totalTaskCount, "已生成本周")
    }

    fun generatePreviewPlans(date: String, type: Int = StudyPlan.TYPE_DAILY): List<StudyPlan> {
        return generatePlansForContext(contextBuilder.buildForDate(date), type)
    }

    private fun generatePlansForContext(planContext: StudyPlanContext, planType: Int): List<StudyPlan> {
        val minStart = parseTime(planContext.generationStartTime) ?: parseTime(planContext.planEarliestTime) ?: 8 * 60
        val maxEnd = parseTime(planContext.planLatestTime) ?: 22 * 60
        if (minStart >= maxEnd) return emptyList()

        val plans = mutableListOf<StudyPlan>()
        val courseBlocks = planContext.courses.mapNotNull { course ->
            courseBlock(planContext, course)?.let { course to it }
        }

        for ((course, block) in courseBlocks.sortedBy { it.second.start }) {
            val start = max(block.start, minStart)
            val end = min(block.end, maxEnd)
            if (end <= start) continue
            val courseLearningTask = courseLearningTaskFor(course, planContext)
            plans += StudyPlan(
                title = courseLearningTask?.title ?: "上课: ${course.name}",
                planDate = planContext.date,
                plannedMinutes = end - start,
                startTime = formatTime(start),
                endTime = formatTime(end),
                type = planType,
                sourceType = StudyPlan.SOURCE_AUTO
            )
        }

        val occupied = (courseBlocks.map { it.second } + existingPlanBlocks(planContext))
            .map { TimeBlock(max(it.start, minStart), min(it.end, maxEnd)) }
            .filter { it.end > it.start }
            .sortedBy { it.start }
        var cursor = minStart
        val ordinaryTasks = planContext.tasks.filterNot { isCourseLearningTask(it, planContext) }

        for (task in ordinaryTasks.take(6)) {
            val duration = estimateTaskDuration(task).coerceIn(30, 120)
            val slot = nextFreeSlot(cursor, duration, maxEnd, occupied) ?: break
            plans += StudyPlan(
                title = task.title,
                planDate = planContext.date,
                plannedMinutes = slot.end - slot.start,
                startTime = formatTime(slot.start),
                endTime = formatTime(slot.end),
                type = planType,
                sourceType = StudyPlan.SOURCE_AUTO
            )
            cursor = slot.end + 10
        }

        if (plans.isEmpty()) {
            nextFreeSlot(minStart, 60, maxEnd, occupied)?.let { slot ->
                plans += StudyPlan(
                    title = "自主学习时间",
                    planDate = planContext.date,
                    plannedMinutes = slot.end - slot.start,
                    startTime = formatTime(slot.start),
                    endTime = formatTime(slot.end),
                    type = planType,
                    sourceType = StudyPlan.SOURCE_AUTO
                )
            }
        }

        return plans.sortedWith(compareBy<StudyPlan> { it.planDate }.thenBy { it.startTime ?: "" })
    }

    private fun courseBlock(planContext: StudyPlanContext, course: Course): TimeBlock? {
        val range = planContext.courseTimeRanges[course.id] ?: return null
        val match = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""").find(range) ?: return null
        val start = toMinutes(match.groupValues[1], match.groupValues[2]) ?: return null
        val end = toMinutes(match.groupValues[3], match.groupValues[4]) ?: return null
        return TimeBlock(start, end).takeIf { it.end > it.start }
    }

    private fun existingPlanBlocks(planContext: StudyPlanContext): List<TimeBlock> {
        return planContext.existingPlans.mapNotNull { plan ->
            val start = parseTime(plan.startTime) ?: return@mapNotNull null
            val end = parseTime(plan.endTime) ?: return@mapNotNull null
            TimeBlock(start, end).takeIf { it.end > it.start }
        }
    }

    private fun nextFreeSlot(startAt: Int, duration: Int, maxEnd: Int, occupied: List<TimeBlock>): TimeBlock? {
        var cursor = startAt
        for (block in occupied) {
            if (cursor + duration <= block.start) return TimeBlock(cursor, cursor + duration)
            if (cursor < block.end) cursor = block.end + 10
        }
        return if (cursor + duration <= maxEnd) TimeBlock(cursor, cursor + duration) else null
    }

    private fun isCourseLearningTask(task: StudyTask, planContext: StudyPlanContext): Boolean {
        val courseId = task.courseId ?: return false
        val courseName = planContext.coursesById[courseId]?.name ?: return false
        val title = task.title
        return planContext.courses.any { it.id == courseId } &&
            title.contains(courseName, ignoreCase = true) &&
            COURSE_LEARNING_KEYWORDS.any { title.contains(it, ignoreCase = true) }
    }

    private fun courseLearningTaskFor(course: Course, planContext: StudyPlanContext): StudyTask? {
        return planContext.tasks.firstOrNull { task ->
            task.courseId == course.id && isCourseLearningTask(task, planContext)
        }
    }

    private fun estimateTaskDuration(task: StudyTask): Int {
        return when (task.type) {
            StudyTask.TYPE_HOMEWORK -> 60
            StudyTask.TYPE_EXPERIMENT -> 120
            StudyTask.TYPE_EXAM -> 120
            StudyTask.TYPE_REVIEW -> 90
            StudyTask.TYPE_PROJECT -> 120
            else -> 45
        }
    }

    private fun buildResult(
        plans: List<StudyPlan>,
        courseCount: Int,
        taskCount: Int,
        prefix: String
    ): PlanGenerationResult {
        val totalMinutes = plans.sumOf { it.plannedMinutes }
        return PlanGenerationResult(
            success = plans.isNotEmpty(),
            plans = plans,
            courseCount = courseCount,
            taskCount = taskCount,
            totalPlannedMinutes = totalMinutes,
            message = if (plans.isNotEmpty()) {
                "$prefix ${plans.size} 个计划项，覆盖 $courseCount 节课和 $taskCount 项任务，共 $totalMinutes 分钟"
            } else {
                "当前时间窗口内没有可生成的计划"
            }
        )
    }

    private fun parseTime(value: String?): Int? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(value?.trim().orEmpty()) ?: return null
        return toMinutes(match.groupValues[1], match.groupValues[2])
    }

    private fun toMinutes(hourText: String, minuteText: String): Int? {
        val hour = hourText.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = minuteText.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }

    private fun formatTime(minutes: Int): String {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    }

    companion object {
        private val COURSE_LEARNING_KEYWORDS = listOf("上课", "课程学习", "完成课程学习", "课堂", "听课")
    }
}
