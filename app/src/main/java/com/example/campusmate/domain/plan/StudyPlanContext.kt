package com.example.campusmate.domain.plan

import android.content.Context
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.CourseRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.StudyPlanRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.data.repository.TaskRepository
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.util.DateTimeUtils

data class StudyPlanContext(
    val date: String,
    val weekday: Int,
    val weekdayName: String,
    val dailyGoalMinutes: Int,
    val courses: List<Course>,
    val tasks: List<StudyTask>,
    val weather: WeatherResult?,
    val recentStudyRecords: List<StudyRecord>,
    val existingPlans: List<StudyPlan>,
    val coursesById: Map<Long, Course>,
    val courseTimeRanges: Map<Long, String>
) {
    fun toPromptText(maxTasks: Int = 12): String {
        return """
请根据以下上下文为 $date（$weekdayName）生成学习计划。

## 当天课程
${coursesPromptText()}

## 课程占用时间（必须避开）
${courseBusyPromptText()}

## 待办任务
${tasksPromptText(maxTasks)}

## 天气
${weatherPromptText()}

## 学习目标
每日学习目标: $dailyGoalMinutes 分钟

## 近 7 天学习记录
${recordsPromptText()}

## 当天已有计划
${existingPlansPromptText()}

## 硬性约束
- 生成的学习计划 startTime/endTime 不得与“课程占用时间”发生任何重叠。
- 如任务需要围绕某门课安排，请放在该课程开始前、结束后或两段课程之间的空档。
- 已有计划和课程共同视为占用时间；无法完全避开时，请缩短计划或改到其他空档，不要覆盖课程。
        """.trimIndent()
    }

    fun courseNameForTask(task: StudyTask): String? {
        return task.courseId?.let(coursesById::get)?.name
    }

    private fun coursesPromptText(): String {
        if (courses.isEmpty()) return "当天无课程安排"
        return courses.joinToString("\n") { course ->
            buildList {
                add("课程: ${course.name}")
                add("教师: ${course.teacher ?: "未指定"}")
                add("地点: ${course.classroom ?: "未指定"}")
                add("节次: ${course.startSection}-${course.endSection}")
                courseTimeRanges[course.id]?.let { add("占用时间: $it") }
                add("周次: ${course.startWeek}-${course.endWeek}")
                add("单双周: ${weekTypeName(course.weekType)}")
                course.note?.takeIf { it.isNotBlank() }?.let { add("备注: $it") }
            }.joinToString(", ")
        }
    }

    private fun courseBusyPromptText(): String {
        if (courses.isEmpty()) return "无课程占用时间"
        return courses.joinToString("\n") { course ->
            val range = courseTimeRanges[course.id] ?: "第 ${course.startSection}-${course.endSection} 节"
            "$range ${course.name}"
        }
    }

    private fun tasksPromptText(maxTasks: Int): String {
        if (tasks.isEmpty()) return "暂无相关待办任务"
        return tasks.take(maxTasks).joinToString("\n") { task ->
            buildList {
                add("任务: ${task.title}")
                add("类型: ${taskTypeName(task.type)}")
                add("优先级: ${priorityName(task.priority)}")
                courseNameForTask(task)?.let { add("关联课程: $it") }
                task.dueAt?.let { add("截止: ${DateTimeUtils.formatDateTime(it)}") }
                task.remindAt?.let { add("提醒: ${DateTimeUtils.formatDateTime(it)}") }
                task.description?.takeIf { it.isNotBlank() }?.let { add("说明: $it") }
            }.joinToString(", ")
        }
    }

    private fun weatherPromptText(): String {
        val value = weather ?: return "暂无天气数据，请按普通学习日安排"
        return buildList {
            add("${value.city}: ${value.weatherText}")
            add("温度 ${value.temperature}")
            add("湿度 ${value.humidity}")
            add("风力 ${value.wind}")
            add("更新 ${DateTimeUtils.formatDateTime(value.updatedAt)}")
            add("来源 ${value.source}")
        }.joinToString(", ")
    }

    private fun recordsPromptText(): String {
        if (recentStudyRecords.isEmpty()) return "近 7 天暂无学习记录"
        return recentStudyRecords
            .groupBy { it.recordDate }
            .toSortedMap()
            .map { (date, records) ->
                val minutes = records.sumOf { it.durationSec } / 60
                "$date: ${minutes} 分钟，${records.size} 条记录"
            }
            .joinToString("\n")
    }

    private fun existingPlansPromptText(): String {
        if (existingPlans.isEmpty()) return "当天暂无已有计划"
        return existingPlans.joinToString("\n") { plan ->
            val time = listOfNotNull(plan.startTime, plan.endTime).joinToString("-").ifBlank { "未设置时间" }
            "$time ${plan.title}，${plan.plannedMinutes} 分钟，状态 ${planStatusName(plan.status)}"
        }
    }

    companion object {
        fun taskTypeName(type: Int): String {
            return when (type) {
                StudyTask.TYPE_HOMEWORK -> "作业"
                StudyTask.TYPE_EXPERIMENT -> "实验"
                StudyTask.TYPE_EXAM -> "考试"
                StudyTask.TYPE_REVIEW -> "复习"
                StudyTask.TYPE_PROJECT -> "项目"
                else -> "其他"
            }
        }

        fun priorityName(priority: Int): String {
            return when (priority) {
                StudyTask.PRIORITY_HIGH -> "高"
                StudyTask.PRIORITY_NORMAL -> "中"
                else -> "低"
            }
        }

        fun weekdayName(weekday: Int): String {
            val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            return names.getOrElse(weekday - 1) { "周一" }
        }

        fun weekTypeName(weekType: Int): String {
            return when (weekType) {
                Course.WEEK_TYPE_ODD -> "单周"
                Course.WEEK_TYPE_EVEN -> "双周"
                else -> "每周"
            }
        }

        fun planStatusName(status: Int): String {
            return when (status) {
                StudyPlan.STATUS_COMPLETED -> "已完成"
                else -> "未完成"
            }
        }
    }
}

class StudyPlanContextBuilder(context: Context) {
    private val courseRepository = CourseRepository(context)
    private val taskRepository = TaskRepository(context)
    private val studyRecordRepository = StudyRecordRepository(context)
    private val studyPlanRepository = StudyPlanRepository(context)
    private val settingsRepository = SettingsRepository(context)
    private val weatherRepository = WeatherRepository(context)

    fun buildForDate(date: String): StudyPlanContext {
        val normalizedDate = normalizeDate(date)
        val weekday = DateTimeUtils.weekdayForDate(normalizedDate)
        val allCourses = courseRepository.getAllCourses()
        val courses = allCourses.filter { it.weekday == weekday }
            .sortedBy { it.startSection }
        val courseIds = courses.map { it.id }.toSet()
        val allTasks = taskRepository.getAllTasks()
        val tasks = sortTasksForDate(allTasks, normalizedDate, courseIds)
        val recordStartDate = DateTimeUtils.datePlusDays(normalizedDate, -6)
        val weatherCity = settingsRepository.getWeatherCity()

        return StudyPlanContext(
            date = normalizedDate,
            weekday = weekday,
            weekdayName = StudyPlanContext.weekdayName(weekday),
            dailyGoalMinutes = settingsRepository.getDailyGoalMinutes(),
            courses = courses,
            tasks = tasks,
            weather = weatherRepository.getCachedWeather(weatherCity) ?: weatherRepository.getCachedWeather(),
            recentStudyRecords = studyRecordRepository.getRecordsBetween(recordStartDate, normalizedDate),
            existingPlans = studyPlanRepository.getPlansByDate(normalizedDate),
            coursesById = allCourses.associateBy { it.id },
            courseTimeRanges = courses.associate { it.id to courseTimeRange(it) }
        )
    }

    private fun normalizeDate(date: String): String {
        return DateTimeUtils.parseDateMillis(date)?.let(DateTimeUtils::formatDate) ?: DateTimeUtils.todayDate()
    }

    private fun courseTimeRange(course: Course): String {
        val configuredSlots = settingsRepository.getSectionTimeSlots().associateBy { it.section }
        val startTime = configuredSlots[course.startSection]?.startTime
            ?: DEFAULT_SECTION_TIMES[course.startSection]?.first
        val endTime = configuredSlots[course.endSection]?.endTime
            ?: DEFAULT_SECTION_TIMES[course.endSection]?.second
        return if (startTime != null && endTime != null) {
            "$startTime-$endTime"
        } else {
            "第 ${course.startSection}-${course.endSection} 节"
        }
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private val DEFAULT_SECTION_TIMES = mapOf(
            1 to ("08:00" to "08:45"),
            2 to ("08:50" to "09:35"),
            3 to ("09:50" to "10:35"),
            4 to ("10:40" to "11:25"),
            5 to ("11:30" to "12:15"),
            6 to ("13:30" to "14:15"),
            7 to ("14:20" to "15:05"),
            8 to ("15:20" to "16:05"),
            9 to ("16:10" to "16:55"),
            10 to ("17:00" to "17:45"),
            11 to ("19:00" to "19:45"),
            12 to ("19:50" to "20:35")
        )

        fun sortTasksForDate(
            tasks: List<StudyTask>,
            date: String,
            courseIds: Set<Long> = emptySet()
        ): List<StudyTask> {
            val dateMillis = DateTimeUtils.parseDateMillis(date) ?: DateTimeUtils.nowMillis()
            val startOfDay = DateTimeUtils.startOfDayMillis(dateMillis)
            val endOfDay = DateTimeUtils.endOfDayMillis(dateMillis)
            val nearDeadline = endOfDay + 7 * DAY_MILLIS

            return tasks
                .filter { task ->
                    task.status == StudyTask.STATUS_TODO &&
                        !task.isDeleted &&
                        shouldIncludeTask(task, endOfDay, nearDeadline, courseIds)
                }
                .sortedWith(
                    compareBy<StudyTask> { dueBucket(it, startOfDay, endOfDay, nearDeadline) }
                        .thenBy { courseRank(it, courseIds) }
                        .thenByDescending { it.priority }
                        .thenBy { it.dueAt ?: Long.MAX_VALUE }
                        .thenBy { it.createdAt }
                )
        }

        private fun shouldIncludeTask(
            task: StudyTask,
            endOfDay: Long,
            nearDeadline: Long,
            courseIds: Set<Long>
        ): Boolean {
            val courseId = task.courseId
            if (courseId != null && courseIds.contains(courseId)) return true
            if (task.priority == StudyTask.PRIORITY_HIGH) return true
            val dueAt = task.dueAt ?: return true
            return dueAt <= nearDeadline || dueAt <= endOfDay
        }

        private fun courseRank(task: StudyTask, courseIds: Set<Long>): Int {
            val courseId = task.courseId
            return if (courseId != null && courseIds.contains(courseId)) 0 else 1
        }

        private fun dueBucket(task: StudyTask, startOfDay: Long, endOfDay: Long, nearDeadline: Long): Int {
            val dueAt = task.dueAt ?: return 4
            return when {
                dueAt < startOfDay -> 0
                dueAt <= endOfDay -> 1
                dueAt <= nearDeadline -> 2
                else -> 3
            }
        }

        fun weekdayForDate(date: String): Int = DateTimeUtils.weekdayForDate(date)

        fun weekdayNameForDate(date: String): String {
            return StudyPlanContext.weekdayName(weekdayForDate(date))
        }

    }
}
