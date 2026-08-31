package com.example.campusmate.domain.ai.context

import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.domain.ai.memory.AiMemoryContext

const val AI_CONTEXT_SCHEMA_VERSION = 2

enum class AiContextPurpose {
    DASHBOARD_ADVICE,
    PLAN_DAY,
    PLAN_WEEK,
    FILE_ANALYSIS
}

data class AiContextBuildRequest(
    val purpose: AiContextPurpose,
    val startDate: String,
    val endDate: String = startDate,
    val recentStudyDays: Int = DEFAULT_RECENT_STUDY_DAYS
) {
    init {
        require(isStrictDate(startDate)) { "startDate must use yyyy-MM-dd" }
        require(isStrictDate(endDate)) { "endDate must use yyyy-MM-dd" }
        require(endDate >= startDate) { "endDate must not be before startDate" }
        require(recentStudyDays in 1..MAX_RECENT_STUDY_DAYS) {
            "recentStudyDays must be between 1 and $MAX_RECENT_STUDY_DAYS"
        }
    }

    companion object {
        private const val DEFAULT_RECENT_STUDY_DAYS = 14
        private const val MAX_RECENT_STUDY_DAYS = 30
        private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

        fun dashboard(
            anchorDate: String = DateTimeUtils.todayDate(),
            horizonDays: Int = 3
        ): AiContextBuildRequest {
            require(horizonDays > 0) { "horizonDays must be positive" }
            return AiContextBuildRequest(
                purpose = AiContextPurpose.DASHBOARD_ADVICE,
                startDate = anchorDate,
                endDate = DateTimeUtils.datePlusDays(anchorDate, horizonDays - 1)
            )
        }

        private fun isStrictDate(value: String): Boolean {
            return DATE_PATTERN.matches(value) && DateTimeUtils.parseDateMillis(value) != null
        }
    }
}

data class AiContextSnapshot(
    val schemaVersion: Int = AI_CONTEXT_SCHEMA_VERSION,
    val generatedAt: Long,
    val zoneId: String,
    val purpose: AiContextPurpose,
    val rangeStart: String,
    val rangeEnd: String,
    val settings: AiPlanningSettings,
    val days: List<AiDayContext>,
    val pendingTasks: List<AiTaskFact>,
    val learningProgress: AiLearningProgress,
    val weather: AiWeatherFact?,
    val warnings: List<AiContextWarning>,
    val omitted: AiContextOmissions,
    val memoryContext: AiMemoryContext = AiMemoryContext()
) {
    val allowedMemoryRefs: Set<String>
        get() = if (purpose == AiContextPurpose.FILE_ANALYSIS) emptySet() else memoryContext.allowedMemoryRefs

    val allowedLocalRefs: Set<String>
        get() = buildSet {
            days.forEach { day ->
                day.courses.mapTo(this) { it.localRef }
                day.existingPlans.mapTo(this) { it.localRef }
            }
            pendingTasks.mapTo(this) { it.localRef }
        }
}

data class AiPlanningSettings(
    val dailyGoalMinutes: Int,
    val earliestPlanTime: String,
    val latestPlanTime: String,
    val weatherCity: String
)

data class AiDayContext(
    val date: String,
    val weekday: Int,
    val weekdayName: String,
    val courses: List<AiCourseFact>,
    val existingPlans: List<AiPlanFact>,
    val busyWindows: List<AiBusyWindow>,
    val occupiedTimeRanges: List<AiOccupiedTimeRange>
)

data class AiCourseFact(
    val localRef: String,
    val name: String,
    val teacher: String?,
    val classroom: String?,
    val startSection: Int,
    val endSection: Int,
    val timeRange: String,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: String
)

data class AiTaskFact(
    val localRef: String,
    val courseRef: String?,
    val courseName: String?,
    val title: String,
    val description: String?,
    val type: String,
    val priority: String,
    val dueAt: Long?,
    val remindAt: Long?,
    val overdue: Boolean
)

data class AiPlanFact(
    val localRef: String,
    val title: String,
    val startTime: String?,
    val endTime: String?,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val status: String
)

data class AiBusyWindow(
    val startTime: String,
    val endTime: String,
    val type: AiBusyWindowType,
    val label: String,
    val localRef: String
)

enum class AiBusyWindowType {
    COURSE,
    EXISTING_PLAN
}

data class AiOccupiedTimeRange(
    val startTime: String,
    val endTime: String
)

data class AiDailyStudyFact(
    val date: String,
    val minutes: Int,
    val sessionCount: Int
)

data class AiLearningProgress(
    val days: List<AiDailyStudyFact>,
    val totalMinutes: Int,
    val averageMinutesPerDay: Int,
    val activeDays: Int,
    val currentStreakDays: Int,
    val goalHitDays: Int,
    val trend: AiLearningTrend
)

enum class AiLearningTrend {
    UP,
    STEADY,
    DOWN,
    INSUFFICIENT_DATA
}

data class AiWeatherFact(
    val city: String,
    val weatherText: String,
    val temperature: String,
    val humidity: String,
    val wind: String,
    val source: String,
    val updatedAt: Long,
    val ageMillis: Long,
    val freshness: AiWeatherFreshness,
    val usableForRealtimeAdvice: Boolean
)

enum class AiWeatherFreshness {
    FRESH,
    STALE
}

data class AiContextOmissions(
    val courses: Int = 0,
    val tasks: Int = 0,
    val plans: Int = 0
) {
    val total: Int
        get() = courses + tasks + plans
}

data class AiContextWarning(
    val code: AiContextWarningCode,
    val message: String
)

enum class AiContextWarningCode {
    DATE_RANGE_CLIPPED,
    COURSE_WEEK_UNRESOLVED,
    STALE_WEATHER,
    WEATHER_UNAVAILABLE,
    WEATHER_CITY_MISMATCH,
    WEATHER_TIMESTAMP_INVALID,
    ITEMS_OMITTED
}
