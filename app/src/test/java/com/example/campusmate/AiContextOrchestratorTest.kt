package com.example.campusmate

import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.data.repository.SettingsSectionTimeSlot
import com.example.campusmate.domain.ai.context.AiContextBuildRequest
import com.example.campusmate.domain.ai.context.AiContextDataSource
import com.example.campusmate.domain.ai.context.AiContextJsonRenderer
import com.example.campusmate.domain.ai.context.AiContextOrchestrator
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.context.AiContextSettingsSource
import com.example.campusmate.domain.ai.context.AiContextWarningCode
import com.example.campusmate.domain.ai.context.AiLearningTrend
import com.example.campusmate.domain.ai.context.AiWeatherFreshness
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextOrchestratorTest {
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }

    @Test
    fun build_groupsScheduleSelectsTasksAndReadsEachSourceOnce() {
        val now = millis("2026-06-08 10:00")
        val source = FakeAiContextDataSource(
            courses = listOf(
                course(1L, "高等数学", weekday = 1, startSection = 1, endSection = 2),
                course(2L, "大学物理", weekday = 3, startSection = 3, endSection = 4)
            ),
            tasks = listOf(
                task(1L, "逾期报告", dueAt = millis("2026-06-07 20:00")),
                task(2L, "数学作业", dueAt = millis("2026-06-08 18:00"), courseId = 1L),
                task(3L, "远期高优任务", dueAt = millis("2026-07-01 12:00"), priority = StudyTask.PRIORITY_HIGH),
                task(4L, "远期普通任务", dueAt = millis("2026-07-02 12:00")),
                task(5L, "已完成任务", dueAt = null, status = StudyTask.STATUS_DONE),
                task(6L, "已删除任务", dueAt = null, isDeleted = true),
                task(7L, "长期阅读", dueAt = null)
            ),
            plans = listOf(
                plan(11L, "无时间阅读", "2026-06-08", startTime = null, endTime = null),
                plan(10L, "下午复习", "2026-06-08", startTime = "15:00", endTime = "16:00")
            ),
            records = studyRecords(
                startDate = "2026-05-26",
                firstWeekMinutes = 100,
                secondWeekMinutes = 200
            ),
            weather = weather("北京", now - 5 * 60_000L),
            settings = contextSettings(
                sectionTimeSlots = listOf(
                    SettingsSectionTimeSlot(1, "07:30", "08:15"),
                    SettingsSectionTimeSlot(2, "08:20", "09:05")
                )
            )
        )

        val snapshot = AiContextOrchestrator(source) { now }.build(
            AiContextBuildRequest(
                purpose = AiContextPurpose.DASHBOARD_ADVICE,
                startDate = "2026-06-08",
                endDate = "2026-06-10",
                recentStudyDays = 14
            )
        )

        assertEquals(listOf("2026-06-08", "2026-06-09", "2026-06-10"), snapshot.days.map { it.date })
        assertEquals("07:30-09:05", snapshot.days.first().courses.single().timeRange)
        assertEquals("大学物理", snapshot.days.last().courses.single().name)
        assertEquals(listOf("plan:10", "plan:11"), snapshot.days.first().existingPlans.map { it.localRef })
        assertEquals(listOf("task:1", "task:2", "task:3", "task:7"), snapshot.pendingTasks.map { it.localRef })
        assertTrue(snapshot.pendingTasks.first().overdue)
        assertEquals(2_100, snapshot.learningProgress.totalMinutes)
        assertEquals(150, snapshot.learningProgress.averageMinutesPerDay)
        assertEquals(14, snapshot.learningProgress.currentStreakDays)
        assertEquals(AiLearningTrend.UP, snapshot.learningProgress.trend)
        assertEquals(AiWeatherFreshness.FRESH, snapshot.weather?.freshness)
        assertTrue(snapshot.weather?.usableForRealtimeAdvice == true)
        assertTrue(snapshot.allowedLocalRefs.containsAll(setOf("course:1", "task:2", "plan:10")))
        assertTrue(snapshot.warnings.any { it.code == AiContextWarningCode.COURSE_WEEK_UNRESOLVED })

        assertEquals(1, source.courseReads)
        assertEquals(1, source.taskReads)
        assertEquals(1, source.planReads)
        assertEquals(1, source.recordReads)
        assertEquals(1, source.weatherReads)
        assertEquals(1, source.settingsReads)
        assertEquals("2026-06-08" to "2026-06-10", source.planRange)
        assertEquals("2026-05-26" to "2026-06-08", source.recordRange)
        assertEquals("北京", source.weatherCity)

        val rendered = AiContextJsonRenderer.render(snapshot)
        val json = JSONObject(rendered)
        assertEquals(2, json.getInt("schemaVersion"))
        assertEquals("2026-06-08 10:00", json.getString("generatedAt"))
        assertEquals(4, json.getJSONArray("pendingTasks").length())
        assertTrue(json.getJSONObject("weather").getBoolean("usableForRealtimeAdvice"))
        assertFalse(rendered.contains("rawJson"))
    }

    @Test
    fun build_clipsRangeCapsItemsAndMarksStaleWeather() {
        val now = millis("2026-06-08 10:00")
        val source = FakeAiContextDataSource(
            courses = (1L..14L).map { id -> course(id, "课程$id", weekday = 1) },
            tasks = (1L..14L).map { id -> task(id, "任务$id", dueAt = null) },
            plans = (1L..14L).map { id ->
                val (start, end) = when (id) {
                    13L -> "19:00" to "20:00"
                    14L -> "20:00" to "21:00"
                    else -> "15:00" to "16:00"
                }
                plan(id, "计划$id", "2026-06-08", startTime = start, endTime = end)
            },
            weather = weather("北京", now - 30 * 60_000L - 1L)
        )

        val snapshot = AiContextOrchestrator(source) { now }.build(
            AiContextBuildRequest(
                purpose = AiContextPurpose.DASHBOARD_ADVICE,
                startDate = "2026-06-08",
                endDate = "2026-06-30"
            )
        )

        assertEquals("2026-06-10", snapshot.rangeEnd)
        assertEquals(12, snapshot.days.first().courses.size)
        assertEquals(12, snapshot.days.first().existingPlans.size)
        assertEquals(12, snapshot.pendingTasks.size)
        assertEquals(2, snapshot.omitted.courses)
        assertEquals(2, snapshot.omitted.tasks)
        assertEquals(2, snapshot.omitted.plans)
        assertTrue(
            snapshot.days.first().occupiedTimeRanges.any {
                it.startTime == "19:00" && it.endTime == "21:00"
            }
        )
        assertEquals(AiWeatherFreshness.STALE, snapshot.weather?.freshness)
        assertFalse(snapshot.weather?.usableForRealtimeAdvice ?: true)
        assertTrue(snapshot.warnings.any { it.code == AiContextWarningCode.DATE_RANGE_CLIPPED })
        assertTrue(snapshot.warnings.any { it.code == AiContextWarningCode.STALE_WEATHER })
        assertTrue(snapshot.warnings.any { it.code == AiContextWarningCode.ITEMS_OMITTED })
    }

    @Test
    fun build_excludesMismatchedWeatherAndRequestRejectsLooseDates() {
        val now = millis("2026-06-08 10:00")
        val source = FakeAiContextDataSource(weather = weather("上海", now))

        val snapshot = AiContextOrchestrator(source) { now }.build(
            AiContextBuildRequest(
                purpose = AiContextPurpose.PLAN_DAY,
                startDate = "2026-06-08"
            )
        )

        assertNull(snapshot.weather)
        assertTrue(snapshot.warnings.any { it.code == AiContextWarningCode.WEATHER_CITY_MISMATCH })
        assertThrows(IllegalArgumentException::class.java) {
            AiContextBuildRequest(
                purpose = AiContextPurpose.PLAN_DAY,
                startDate = "2026-6-8"
            )
        }
    }

    @Test
    fun build_capsFutureLearningHistoryAtCurrentDate() {
        val now = millis("2026-06-08 10:00")
        val source = FakeAiContextDataSource()

        val snapshot = AiContextOrchestrator(source) { now }.build(
            AiContextBuildRequest(
                purpose = AiContextPurpose.PLAN_DAY,
                startDate = "2026-06-10",
                recentStudyDays = 7
            )
        )

        assertEquals("2026-06-02" to "2026-06-08", source.recordRange)
        assertEquals("2026-06-08", snapshot.learningProgress.days.last().date)
    }

    @Test
    fun build_rejectsFarFutureWeatherTimestamp() {
        val now = millis("2026-06-08 10:00")
        val source = FakeAiContextDataSource(
            weather = weather("北京", now + 6 * 60_000L)
        )

        val snapshot = AiContextOrchestrator(source) { now }.build(
            AiContextBuildRequest(AiContextPurpose.PLAN_DAY, "2026-06-08")
        )

        assertNull(snapshot.weather)
        assertTrue(
            snapshot.warnings.any { it.code == AiContextWarningCode.WEATHER_TIMESTAMP_INVALID }
        )
    }

    @Test
    fun build_usesInsufficientTrendWhenComparisonWindowsDiffer() {
        val now = millis("2026-06-08 10:00")
        val records = (0 until 8).map { offset ->
            StudyRecord(
                id = offset + 1L,
                durationSec = 60 * 60,
                recordDate = DateTimeUtils.datePlusDays("2026-06-01", offset)
            )
        }
        val source = FakeAiContextDataSource(records = records)

        val snapshot = AiContextOrchestrator(source) { now }.build(
            AiContextBuildRequest(
                purpose = AiContextPurpose.PLAN_DAY,
                startDate = "2026-06-08",
                recentStudyDays = 8
            )
        )

        assertEquals(AiLearningTrend.INSUFFICIENT_DATA, snapshot.learningProgress.trend)
    }

    @Test
    fun fileAnalysis_omitsUnrelatedPersonalContextAndDoesNotReadIt() {
        val now = millis("2026-06-08 10:00")
        val source = FakeAiContextDataSource(
            courses = listOf(
                Course(
                    id = 1L,
                    name = "数据结构",
                    teacher = "张老师",
                    classroom = "海淀教室",
                    weekday = 1,
                    startSection = 1,
                    endSection = 2
                )
            ),
            tasks = listOf(
                StudyTask(
                    id = 1L,
                    courseId = 1L,
                    title = "提交实验",
                    description = "秘密说明",
                    remindAt = now + 60_000L,
                    status = StudyTask.STATUS_TODO
                )
            ),
            records = listOf(
                StudyRecord(id = 1L, durationSec = 3_600, recordDate = "2026-06-08")
            ),
            plans = listOf(
                plan(
                    id = 1L,
                    title = "秘密个人计划",
                    date = "2026-06-08",
                    startTime = "18:00",
                    endTime = "19:00"
                )
            ),
            weather = weather("北京", now)
        )

        val snapshot = AiContextOrchestrator(source) { now }.build(
            AiContextBuildRequest(AiContextPurpose.FILE_ANALYSIS, "2026-06-08")
        )
        val rendered = AiContextJsonRenderer.render(snapshot)

        assertEquals(0, source.recordReads)
        assertEquals(0, source.weatherReads)
        assertEquals(0, source.planReads)
        assertFalse(rendered.contains("\"settings\""))
        assertFalse(rendered.contains("\"weather\""))
        assertFalse(rendered.contains("\"learningProgress\""))
        assertFalse(rendered.contains("张老师"))
        assertFalse(rendered.contains("海淀教室"))
        assertFalse(rendered.contains("秘密说明"))
        assertFalse(rendered.contains("秘密个人计划"))
        assertFalse(rendered.contains("\"existingPlans\""))
        assertFalse(rendered.contains("\"busyWindows\""))
        assertFalse(rendered.contains("北京"))
        assertTrue(rendered.contains("\"occupiedTimeRanges\""))
        assertTrue(rendered.contains("\"startTime\": \"08:00\""))
        assertTrue(rendered.contains("数据结构"))
        assertTrue(rendered.contains("提交实验"))
    }

    private fun course(
        id: Long,
        name: String,
        weekday: Int,
        startSection: Int = 1,
        endSection: Int = 2
    ): Course {
        return Course(
            id = id,
            name = name,
            weekday = weekday,
            startSection = startSection,
            endSection = endSection,
            startWeek = 1,
            endWeek = 16
        )
    }

    private fun task(
        id: Long,
        title: String,
        dueAt: Long?,
        courseId: Long? = null,
        priority: Int = StudyTask.PRIORITY_NORMAL,
        status: Int = StudyTask.STATUS_TODO,
        isDeleted: Boolean = false
    ): StudyTask {
        return StudyTask(
            id = id,
            courseId = courseId,
            title = title,
            priority = priority,
            dueAt = dueAt,
            status = status,
            createdAt = id,
            isDeleted = isDeleted
        )
    }

    private fun plan(
        id: Long,
        title: String,
        date: String,
        startTime: String?,
        endTime: String?
    ): StudyPlan {
        return StudyPlan(
            id = id,
            title = title,
            planDate = date,
            plannedMinutes = 60,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun studyRecords(
        startDate: String,
        firstWeekMinutes: Int,
        secondWeekMinutes: Int
    ): List<StudyRecord> {
        return (0 until 14).map { offset ->
            StudyRecord(
                id = offset + 1L,
                durationSec = (if (offset < 7) firstWeekMinutes else secondWeekMinutes) * 60,
                recordDate = DateTimeUtils.datePlusDays(startDate, offset)
            )
        }
    }

    private fun weather(city: String, updatedAt: Long): WeatherResult {
        return WeatherResult(
            city = city,
            weatherText = "晴",
            temperature = "26°C",
            humidity = "40%",
            wind = "东风 2 级",
            source = "test",
            rawJson = "{\"secret\":\"must-not-render\"}",
            updatedAt = updatedAt
        )
    }

    private fun contextSettings(
        sectionTimeSlots: List<SettingsSectionTimeSlot> = emptyList()
    ): AiContextSettingsSource {
        return AiContextSettingsSource(
            dailyGoalMinutes = 60,
            earliestPlanTime = "08:00",
            latestPlanTime = "22:00",
            weatherCity = "北京",
            sectionTimeSlots = sectionTimeSlots,
            zoneId = "Asia/Shanghai"
        )
    }

    private fun millis(value: String): Long = dateTimeFormat.parse(value)!!.time

    private inner class FakeAiContextDataSource(
        private val courses: List<Course> = emptyList(),
        private val tasks: List<StudyTask> = emptyList(),
        private val plans: List<StudyPlan> = emptyList(),
        private val records: List<StudyRecord> = emptyList(),
        private val weather: WeatherResult? = null,
        private val settings: AiContextSettingsSource = contextSettings(),
        private val memories: List<AiMemory> = emptyList()
    ) : AiContextDataSource {
        var courseReads = 0
        var taskReads = 0
        var planReads = 0
        var recordReads = 0
        var weatherReads = 0
        var settingsReads = 0
        var memoryReads = 0

        override fun loadMemories(nowMillis: Long): List<AiMemory> {
            memoryReads += 1
            return memories
        }
        var planRange: Pair<String, String>? = null
        var recordRange: Pair<String, String>? = null
        var weatherCity: String? = null

        override fun loadCourses(): List<Course> {
            courseReads += 1
            return courses
        }

        override fun loadTasks(): List<StudyTask> {
            taskReads += 1
            return tasks
        }

        override fun loadPlans(startDate: String, endDate: String): List<StudyPlan> {
            planReads += 1
            planRange = startDate to endDate
            return plans
        }

        override fun loadStudyRecords(startDate: String, endDate: String): List<StudyRecord> {
            recordReads += 1
            recordRange = startDate to endDate
            return records
        }

        override fun loadCachedWeather(city: String): WeatherResult? {
            weatherReads += 1
            weatherCity = city
            return weather
        }

        override fun loadSettings(): AiContextSettingsSource {
            settingsReads += 1
            return settings
        }
    }
}
