package com.example.campusmate.domain.ai.context

import android.content.Context
import com.example.campusmate.domain.ai.memory.AiMemoryContext
import com.example.campusmate.domain.ai.memory.AiMemoryRetriever
import com.example.campusmate.domain.ai.memory.LearningGrowthSummaryBuilder
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.WeatherRepository
import com.example.campusmate.domain.schedule.CourseTimeResolver
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.util.DateTimeUtils

class AiContextOrchestrator internal constructor(
    private val source: AiContextDataSource,
    private val nowMillisProvider: () -> Long = DateTimeUtils::nowMillis
) {
    constructor(context: Context) : this(RepositoryAiContextDataSource(context))

    fun build(request: AiContextBuildRequest): AiContextSnapshot {
        val generatedAt = nowMillisProvider()
        val policy = AiContextPolicy.forPurpose(request.purpose)
        val requestedDates = datesBetween(request.startDate, request.endDate, policy.maxDays)
        val rangeEnd = requestedDates.last()
        val rangeWasClipped = rangeEnd < request.endDate
        val historyEnd = minOf(request.startDate, DateTimeUtils.formatDate(generatedAt))
        val recentStart = DateTimeUtils.datePlusDays(historyEnd, -(request.recentStudyDays - 1))

        val settingsSource = source.loadSettings()
        val memoryEnabled = policy.includeMemories && settingsSource.memoryEnabled
        val recordStart = if (memoryEnabled) {
            minOf(recentStart, DateTimeUtils.datePlusDays(historyEnd, -(LearningGrowthSummaryBuilder.WINDOW_DAYS - 1)))
        } else {
            recentStart
        }
        val courses = source.loadCourses().filterNot { it.isDeleted }
        val tasks = source.loadTasks()
        val plans = if (policy.includePlans) {
            source.loadPlans(request.startDate, rangeEnd)
        } else {
            emptyList()
        }
        val records = if (policy.includeLearningProgress) {
            source.loadStudyRecords(recordStart, historyEnd)
        } else {
            emptyList()
        }
        val cachedWeather = if (policy.includeWeather) {
            source.loadCachedWeather(settingsSource.weatherCity)
        } else {
            null
        }

        val timeResolver = CourseTimeResolver(settingsSource.sectionTimeSlots)
        val courseById = courses.associateBy(Course::id)
        val coursesByWeekday = courses.groupBy(Course::weekday)
        val plansByDate = plans.groupBy(StudyPlan::planDate)
        var omittedCourses = 0
        var omittedPlans = 0
        val relevantCourseIds = mutableSetOf<Long>()

        val days = requestedDates.map { date ->
            val weekday = DateTimeUtils.weekdayForDate(date)
            val matchingCourses = coursesByWeekday[weekday]
                .orEmpty()
                .sortedWith(
                    compareBy<Course>(Course::startSection)
                        .thenBy(Course::endSection)
                        .thenBy(Course::name)
                        .thenBy(Course::id)
                )
            relevantCourseIds += matchingCourses.map(Course::id)
            val selectedCourses = matchingCourses.take(policy.maxCoursesPerDay)
            omittedCourses += (matchingCourses.size - selectedCourses.size).coerceAtLeast(0)
            val courseFacts = selectedCourses.map { course ->
                course.toAiFact(timeResolver, policy)
            }

            val matchingPlans = plansByDate[date]
                .orEmpty()
                .sortedWith(PLAN_COMPARATOR)
            val selectedPlans = matchingPlans.take(policy.maxPlansPerDay)
            omittedPlans += (matchingPlans.size - selectedPlans.size).coerceAtLeast(0)
            val planFacts = selectedPlans.map { it.toAiFact(policy) }

            AiDayContext(
                date = date,
                weekday = weekday,
                weekdayName = weekdayName(weekday),
                courses = courseFacts,
                existingPlans = planFacts,
                busyWindows = buildBusyWindows(
                    selectedCourses = selectedCourses,
                    selectedPlans = selectedPlans,
                    timeResolver = timeResolver,
                    policy = policy
                ),
                occupiedTimeRanges = buildOccupiedTimeRanges(
                    courses = matchingCourses,
                    plans = matchingPlans,
                    timeResolver = timeResolver
                )
            )
        }

        val selectedTasks = selectTasks(
            tasks = tasks,
            relevantCourseIds = relevantCourseIds,
            rangeStart = request.startDate,
            rangeEnd = rangeEnd,
            generatedAt = generatedAt,
            maxTasks = policy.maxTasks
        )
        val taskFacts = selectedTasks.items.map { task ->
            task.toAiFact(task.courseId?.let(courseById::get), selectedTasks.referenceMillis, policy)
        }
        val memoryContext = if (memoryEnabled) {
            val queryTerms = days.flatMap { day -> day.courses.map(AiCourseFact::name) } +
                taskFacts.flatMap { listOf(it.title, it.courseName.orEmpty(), it.type) }
            val growth = LearningGrowthSummaryBuilder.build(records, historyEnd, settingsSource.dailyGoalMinutes)
            AiMemoryContext(
                memories = AiMemoryRetriever.retrieve(
                    source.loadMemories(generatedAt), request.purpose, queryTerms, generatedAt
                ),
                growth = growth.takeIf { it.sessionCount > 0 }
            )
        } else {
            AiMemoryContext()
        }
        val learningProgress = if (policy.includeLearningProgress) {
            buildLearningProgress(
                records = records,
                rangeEnd = historyEnd,
                dayCount = request.recentStudyDays,
                dailyGoalMinutes = settingsSource.dailyGoalMinutes
            )
        } else {
            emptyLearningProgress()
        }
        val weatherSelection = if (policy.includeWeather) {
            selectWeather(
                cachedWeather = cachedWeather,
                configuredCity = settingsSource.weatherCity,
                generatedAt = generatedAt,
                policy = policy
            )
        } else {
            WeatherSelection(fact = null, warning = null)
        }
        val omissions = AiContextOmissions(
            courses = omittedCourses,
            tasks = selectedTasks.omittedCount,
            plans = omittedPlans
        )
        val warnings = buildList {
            if (rangeWasClipped) {
                add(
                    AiContextWarning(
                        code = AiContextWarningCode.DATE_RANGE_CLIPPED,
                        message = "请求日期范围已按 ${policy.maxDays} 天上限截断到 $rangeEnd。"
                    )
                )
            }
            if (days.any { it.courses.isNotEmpty() }) {
                add(
                    AiContextWarning(
                        code = AiContextWarningCode.COURSE_WEEK_UNRESOLVED,
                        message = "当前未配置学期起始周，课程仅按星期匹配；请结合 startWeek、endWeek 和 weekType 谨慎判断是否实际开课。"
                    )
                )
            }
            weatherSelection.warning?.let(::add)
            if (omissions.total > 0) {
                add(
                    AiContextWarning(
                        code = AiContextWarningCode.ITEMS_OMITTED,
                        message = "为控制上下文长度，另有 ${omissions.courses} 个课程安排、${omissions.tasks} 个任务、${omissions.plans} 个计划未纳入。"
                    )
                )
            }
        }

        return AiContextSnapshot(
            generatedAt = generatedAt,
            zoneId = settingsSource.zoneId,
            purpose = request.purpose,
            rangeStart = request.startDate,
            rangeEnd = rangeEnd,
            settings = AiPlanningSettings(
                dailyGoalMinutes = settingsSource.dailyGoalMinutes,
                earliestPlanTime = settingsSource.earliestPlanTime,
                latestPlanTime = settingsSource.latestPlanTime,
                weatherCity = compact(settingsSource.weatherCity, policy.maxTitleChars) ?: "未设置"
            ),
            days = days,
            pendingTasks = taskFacts,
            learningProgress = learningProgress,
            weather = weatherSelection.fact,
            warnings = warnings,
            omitted = omissions,
            memoryContext = memoryContext
        )
    }

    private fun selectTasks(
        tasks: List<StudyTask>,
        relevantCourseIds: Set<Long>,
        rangeStart: String,
        rangeEnd: String,
        generatedAt: Long,
        maxTasks: Int
    ): SelectedTasks {
        val rangeStartMillis = DateTimeUtils.parseDateMillis(rangeStart)
            ?: error("Validated start date became invalid")
        val rangeStartEndMillis = DateTimeUtils.endOfDayMillis(rangeStartMillis)
        val rangeEndMillis = DateTimeUtils.endOfDayMillis(
            DateTimeUtils.parseDateMillis(rangeEnd) ?: rangeStartMillis
        )
        val today = DateTimeUtils.formatDate(generatedAt)
        val referenceMillis = if (rangeStart == today) generatedAt else rangeStartMillis
        val relevant = tasks
            .asSequence()
            .filter { it.status == StudyTask.STATUS_TODO && !it.isDeleted }
            .filter { task ->
                val dueAt = task.dueAt
                dueAt == null ||
                    dueAt <= rangeEndMillis ||
                    task.priority == StudyTask.PRIORITY_HIGH ||
                    task.courseId?.let(relevantCourseIds::contains) == true
            }
            .sortedWith(
                compareBy<StudyTask> { task ->
                    taskDueBucket(
                        task = task,
                        referenceMillis = referenceMillis,
                        firstDayEndMillis = rangeStartEndMillis,
                        rangeEndMillis = rangeEndMillis,
                        relevantCourseIds = relevantCourseIds
                    )
                }
                    .thenBy { if (it.courseId?.let(relevantCourseIds::contains) == true) 0 else 1 }
                    .thenByDescending(StudyTask::priority)
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy(StudyTask::createdAt)
                    .thenBy(StudyTask::id)
            )
            .toList()
        return SelectedTasks(
            items = relevant.take(maxTasks),
            omittedCount = (relevant.size - maxTasks).coerceAtLeast(0),
            referenceMillis = referenceMillis
        )
    }

    private fun taskDueBucket(
        task: StudyTask,
        referenceMillis: Long,
        firstDayEndMillis: Long,
        rangeEndMillis: Long,
        relevantCourseIds: Set<Long>
    ): Int {
        val dueAt = task.dueAt
        return when {
            dueAt != null && dueAt < referenceMillis -> 0
            dueAt != null && dueAt <= firstDayEndMillis && task.courseId?.let(relevantCourseIds::contains) == true -> 1
            dueAt != null && dueAt <= firstDayEndMillis -> 2
            dueAt != null && dueAt <= rangeEndMillis -> 3
            task.courseId?.let(relevantCourseIds::contains) == true -> 4
            task.priority == StudyTask.PRIORITY_HIGH -> 5
            dueAt == null -> 6
            else -> 7
        }
    }

    private fun buildLearningProgress(
        records: List<StudyRecord>,
        rangeEnd: String,
        dayCount: Int,
        dailyGoalMinutes: Int
    ): AiLearningProgress {
        val rangeStart = DateTimeUtils.datePlusDays(rangeEnd, -(dayCount - 1))
        val secondsByDate = records
            .asSequence()
            .filter { it.recordDate in rangeStart..rangeEnd }
            .groupBy(StudyRecord::recordDate)
            .mapValues { (_, values) -> values.sumOf { it.durationSec.coerceAtLeast(0) } }
        val sessionsByDate = records
            .asSequence()
            .filter { it.recordDate in rangeStart..rangeEnd }
            .groupingBy(StudyRecord::recordDate)
            .eachCount()
        val days = (0 until dayCount).map { offset ->
            val date = DateTimeUtils.datePlusDays(rangeStart, offset)
            AiDailyStudyFact(
                date = date,
                minutes = (secondsByDate[date] ?: 0) / 60,
                sessionCount = sessionsByDate[date] ?: 0
            )
        }
        val totalMinutes = days.sumOf(AiDailyStudyFact::minutes)
        return AiLearningProgress(
            days = days,
            totalMinutes = totalMinutes,
            averageMinutesPerDay = totalMinutes / dayCount,
            activeDays = days.count { it.minutes > 0 },
            currentStreakDays = days.asReversed().takeWhile { it.minutes > 0 }.size,
            goalHitDays = days.count { it.minutes >= dailyGoalMinutes },
            trend = learningTrend(days)
        )
    }

    private fun learningTrend(days: List<AiDailyStudyFact>): AiLearningTrend {
        if (days.size < 14) return AiLearningTrend.INSUFFICIENT_DATA
        val recentDays = days.takeLast(7)
        val previousDays = days.dropLast(7).takeLast(7)
        if (previousDays.isEmpty()) return AiLearningTrend.INSUFFICIENT_DATA
        val recentMinutes = recentDays.sumOf(AiDailyStudyFact::minutes)
        val previousMinutes = previousDays.sumOf(AiDailyStudyFact::minutes)
        if (recentMinutes == 0 && previousMinutes == 0) return AiLearningTrend.INSUFFICIENT_DATA
        if (previousMinutes == 0) return AiLearningTrend.UP
        return when {
            recentMinutes * 10 > previousMinutes * 11 -> AiLearningTrend.UP
            recentMinutes * 10 < previousMinutes * 9 -> AiLearningTrend.DOWN
            else -> AiLearningTrend.STEADY
        }
    }

    private fun selectWeather(
        cachedWeather: WeatherResult?,
        configuredCity: String,
        generatedAt: Long,
        policy: AiContextPolicy
    ): WeatherSelection {
        if (cachedWeather == null || cachedWeather.updatedAt <= 0L) {
            return WeatherSelection(
                fact = null,
                warning = AiContextWarning(
                    code = AiContextWarningCode.WEATHER_UNAVAILABLE,
                    message = "未找到设置城市的可用天气缓存，不能据此判断当前天气。"
                )
            )
        }
        if (!cachedWeather.city.trim().equals(configuredCity.trim(), ignoreCase = true)) {
            return WeatherSelection(
                fact = null,
                warning = AiContextWarning(
                    code = AiContextWarningCode.WEATHER_CITY_MISMATCH,
                    message = "天气缓存城市与设置城市不一致，已排除该天气数据。"
                )
            )
        }
        if (cachedWeather.updatedAt > generatedAt + MAX_FUTURE_WEATHER_SKEW_MILLIS) {
            return WeatherSelection(
                fact = null,
                warning = AiContextWarning(
                    code = AiContextWarningCode.WEATHER_TIMESTAMP_INVALID,
                    message = "天气缓存更新时间明显晚于当前时间，已排除该天气数据。"
                )
            )
        }
        val ageMillis = (generatedAt - cachedWeather.updatedAt).coerceAtLeast(0L)
        val isFresh = ageMillis <= WeatherRepository.CACHE_MAX_AGE_MILLIS
        return WeatherSelection(
            fact = AiWeatherFact(
                city = compact(cachedWeather.city, policy.maxTitleChars) ?: configuredCity,
                weatherText = compact(cachedWeather.weatherText, policy.maxTitleChars).orEmpty(),
                temperature = compact(cachedWeather.temperature, policy.maxTitleChars).orEmpty(),
                humidity = compact(cachedWeather.humidity, policy.maxTitleChars).orEmpty(),
                wind = compact(cachedWeather.wind, policy.maxTitleChars).orEmpty(),
                source = compact(cachedWeather.source, policy.maxTitleChars).orEmpty(),
                updatedAt = cachedWeather.updatedAt,
                ageMillis = ageMillis,
                freshness = if (isFresh) AiWeatherFreshness.FRESH else AiWeatherFreshness.STALE,
                usableForRealtimeAdvice = isFresh
            ),
            warning = if (isFresh) {
                null
            } else {
                AiContextWarning(
                    code = AiContextWarningCode.STALE_WEATHER,
                    message = "天气缓存已超过 30 分钟，只能作为历史参考，不能用于判断当前降雨、温度或出行条件。"
                )
            }
        )
    }

    private fun Course.toAiFact(
        timeResolver: CourseTimeResolver,
        policy: AiContextPolicy
    ): AiCourseFact {
        val time = timeResolver.resolve(this)
        return AiCourseFact(
            localRef = "course:$id",
            name = compact(name, policy.maxTitleChars) ?: "未命名课程",
            teacher = compact(teacher, policy.maxTitleChars).takeIf { policy.includeCourseDetails },
            classroom = compact(classroom, policy.maxTitleChars).takeIf { policy.includeCourseDetails },
            startSection = startSection,
            endSection = endSection,
            timeRange = time.displayText,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekTypeCode(weekType)
        )
    }

    private fun StudyTask.toAiFact(
        course: Course?,
        referenceMillis: Long,
        policy: AiContextPolicy
    ): AiTaskFact {
        return AiTaskFact(
            localRef = "task:$id",
            courseRef = course?.let { "course:${it.id}" },
            courseName = compact(course?.name, policy.maxTitleChars),
            title = compact(title, policy.maxTitleChars) ?: "未命名任务",
            description = compact(description, policy.maxDetailChars).takeIf { policy.includeTaskDetails },
            type = taskTypeCode(type),
            priority = priorityCode(priority),
            dueAt = dueAt,
            remindAt = remindAt.takeIf { policy.includeTaskDetails },
            overdue = dueAt?.let { it < referenceMillis } == true
        )
    }

    private fun StudyPlan.toAiFact(policy: AiContextPolicy): AiPlanFact {
        return AiPlanFact(
            localRef = "plan:$id",
            title = compact(title, policy.maxTitleChars) ?: "未命名计划",
            startTime = startTime,
            endTime = endTime,
            plannedMinutes = plannedMinutes.coerceAtLeast(0),
            actualMinutes = actualMinutes.coerceAtLeast(0),
            status = planStatusCode(status)
        )
    }

    private fun buildBusyWindows(
        selectedCourses: List<Course>,
        selectedPlans: List<StudyPlan>,
        timeResolver: CourseTimeResolver,
        policy: AiContextPolicy
    ): List<AiBusyWindow> {
        val courseWindows = selectedCourses.mapNotNull { course ->
            val time = timeResolver.resolve(course)
            val start = time.startTime ?: return@mapNotNull null
            val end = time.endTime ?: return@mapNotNull null
            AiBusyWindow(
                startTime = start,
                endTime = end,
                type = AiBusyWindowType.COURSE,
                label = compact(course.name, policy.maxTitleChars) ?: "课程",
                localRef = "course:${course.id}"
            )
        }
        val planWindows = selectedPlans.mapNotNull { plan ->
            val start = plan.startTime?.takeIf(TIME_PATTERN::matches) ?: return@mapNotNull null
            val end = plan.endTime?.takeIf(TIME_PATTERN::matches) ?: return@mapNotNull null
            AiBusyWindow(
                startTime = start,
                endTime = end,
                type = AiBusyWindowType.EXISTING_PLAN,
                label = compact(plan.title, policy.maxTitleChars) ?: "已有计划",
                localRef = "plan:${plan.id}"
            )
        }
        return (courseWindows + planWindows).sortedWith(
            compareBy<AiBusyWindow>(AiBusyWindow::startTime)
                .thenBy(AiBusyWindow::endTime)
                .thenBy(AiBusyWindow::localRef)
        )
    }

    private fun buildOccupiedTimeRanges(
        courses: List<Course>,
        plans: List<StudyPlan>,
        timeResolver: CourseTimeResolver
    ): List<AiOccupiedTimeRange> {
        val ranges = buildList {
            courses.forEach { course ->
                val time = timeResolver.resolve(course)
                val start = parseTimeMinutes(time.startTime)
                val end = parseTimeMinutes(time.endTime)
                if (start != null && end != null && end > start) add(start to end)
            }
            plans.forEach { plan ->
                val start = parseTimeMinutes(plan.startTime)
                val end = parseTimeMinutes(plan.endTime)
                if (start != null && end != null && end > start) add(start to end)
            }
        }.sortedBy(Pair<Int, Int>::first)
        if (ranges.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<Int, Int>>()
        ranges.forEach { range ->
            val previous = merged.lastOrNull()
            if (previous == null || range.first > previous.second) {
                merged += range
            } else if (range.second > previous.second) {
                merged[merged.lastIndex] = previous.first to range.second
            }
        }
        return merged.map { (start, end) ->
            AiOccupiedTimeRange(
                startTime = formatTimeMinutes(start),
                endTime = formatTimeMinutes(end)
            )
        }
    }

    private fun parseTimeMinutes(value: String?): Int? {
        val match = TIME_PATTERN.matchEntire(value.orEmpty()) ?: return null
        val (hour, minute) = match.value.split(':').map(String::toInt)
        return hour * 60 + minute
    }

    private fun formatTimeMinutes(value: Int): String {
        return String.format(java.util.Locale.US, "%02d:%02d", value / 60, value % 60)
    }

    private fun emptyLearningProgress(): AiLearningProgress {
        return AiLearningProgress(
            days = emptyList(),
            totalMinutes = 0,
            averageMinutesPerDay = 0,
            activeDays = 0,
            currentStreakDays = 0,
            goalHitDays = 0,
            trend = AiLearningTrend.INSUFFICIENT_DATA
        )
    }

    private fun datesBetween(startDate: String, endDate: String, maxDays: Int): List<String> {
        return buildList {
            var date = startDate
            while (date <= endDate && size < maxDays) {
                add(date)
                date = DateTimeUtils.datePlusDays(date, 1)
            }
        }
    }

    private fun compact(value: String?, maxChars: Int): String? {
        val normalized = value
            ?.trim()
            ?.replace(WHITESPACE_PATTERN, " ")
            ?.take(maxChars)
            .orEmpty()
        return normalized.takeIf(String::isNotBlank)
    }

    private data class SelectedTasks(
        val items: List<StudyTask>,
        val omittedCount: Int,
        val referenceMillis: Long
    )

    private data class WeatherSelection(
        val fact: AiWeatherFact?,
        val warning: AiContextWarning?
    )

    private data class AiContextPolicy(
        val maxDays: Int,
        val maxTasks: Int,
        val maxCoursesPerDay: Int,
        val maxPlansPerDay: Int,
        val maxTitleChars: Int = 120,
        val maxDetailChars: Int = 400,
        val includeLearningProgress: Boolean = true,
        val includeWeather: Boolean = true,
        val includePlans: Boolean = true,
        val includeCourseDetails: Boolean = true,
        val includeTaskDetails: Boolean = true,
        val includeMemories: Boolean = true
    ) {
        companion object {
            fun forPurpose(purpose: AiContextPurpose): AiContextPolicy {
                return when (purpose) {
                    AiContextPurpose.DASHBOARD_ADVICE -> AiContextPolicy(3, 12, 12, 12)
                    AiContextPurpose.PLAN_DAY -> AiContextPolicy(1, 12, 16, 16)
                    AiContextPurpose.PLAN_WEEK -> AiContextPolicy(7, 20, 16, 16)
                    AiContextPurpose.FILE_ANALYSIS -> AiContextPolicy(
                        maxDays = 7,
                        maxTasks = 16,
                        maxCoursesPerDay = 12,
                        maxPlansPerDay = 12,
                        includeLearningProgress = false,
                        includeWeather = false,
                        includePlans = false,
                        includeCourseDetails = false,
                        includeTaskDetails = false,
                        includeMemories = false
                    )
                }
            }
        }
    }

    companion object {
        private val TIME_PATTERN = Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")
        private val WHITESPACE_PATTERN = Regex("""\s+""")
        private const val MAX_FUTURE_WEATHER_SKEW_MILLIS = 5 * 60_000L
        private val PLAN_COMPARATOR = compareBy<StudyPlan> {
            if (it.startTime.isNullOrBlank()) 1 else 0
        }
            .thenBy { it.startTime.orEmpty() }
            .thenBy { it.endTime.orEmpty() }
            .thenBy(StudyPlan::title)
            .thenBy(StudyPlan::id)

        private fun weekdayName(weekday: Int): String {
            return arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                .getOrElse(weekday - 1) { "未知" }
        }

        private fun weekTypeCode(value: Int): String {
            return when (value) {
                Course.WEEK_TYPE_ODD -> "odd"
                Course.WEEK_TYPE_EVEN -> "even"
                else -> "every"
            }
        }

        private fun taskTypeCode(value: Int): String {
            return when (value) {
                StudyTask.TYPE_HOMEWORK -> "homework"
                StudyTask.TYPE_EXPERIMENT -> "experiment"
                StudyTask.TYPE_EXAM -> "exam"
                StudyTask.TYPE_REVIEW -> "review"
                StudyTask.TYPE_PROJECT -> "project"
                else -> "other"
            }
        }

        private fun priorityCode(value: Int): String {
            return when (value) {
                StudyTask.PRIORITY_HIGH -> "high"
                StudyTask.PRIORITY_NORMAL -> "normal"
                else -> "low"
            }
        }

        private fun planStatusCode(value: Int): String {
            return when (value) {
                StudyPlan.STATUS_COMPLETED -> "completed"
                StudyPlan.STATUS_EXPIRED -> "expired"
                else -> "pending"
            }
        }
    }
}
