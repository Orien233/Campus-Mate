package com.example.campusmate.domain.ai.context

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import com.example.campusmate.domain.ai.memory.AiMemoryContextRenderer

object AiContextJsonRenderer {
    fun render(snapshot: AiContextSnapshot): String {
        val fileMinimal = snapshot.purpose == AiContextPurpose.FILE_ANALYSIS
        val root = JSONObject()
            .put("schemaVersion", snapshot.schemaVersion)
            .put("generatedAt", formatDateTime(snapshot.generatedAt, snapshot.zoneId))
            .put("zoneId", snapshot.zoneId)
            .put("purpose", snapshot.purpose.name.lowercase())
            .put(
                "range",
                JSONObject()
                    .put("start", snapshot.rangeStart)
                    .put("end", snapshot.rangeEnd)
            )
            .put(
                "days",
                JSONArray().apply {
                    snapshot.days.forEach { put(it.toJson(includeDetails = !fileMinimal)) }
                }
            )
            .put(
                "pendingTasks",
                JSONArray().apply {
                    snapshot.pendingTasks.forEach {
                        put(it.toJson(snapshot.zoneId, includeDetails = !fileMinimal))
                    }
                }
            )
            .put(
                "warnings",
                JSONArray().apply { snapshot.warnings.forEach { put(it.toJson()) } }
            )
            .put("omitted", snapshot.omitted.toJson())
        if (!fileMinimal) {
            root.put("settings", snapshot.settings.toJson())
            root.put("weather", snapshot.weather?.toJson(snapshot.zoneId) ?: JSONObject.NULL)
            root.put("learningProgress", snapshot.learningProgress.toJson())
            if (snapshot.memoryContext.memories.isNotEmpty() || snapshot.memoryContext.growth != null) {
                root.put("memoryContext", AiMemoryContextRenderer.toJson(snapshot.memoryContext))
            }
        }
        return root.toString(2)
    }

    private fun AiPlanningSettings.toJson(): JSONObject {
        return JSONObject()
            .put("dailyGoalMinutes", dailyGoalMinutes)
            .put("earliestPlanTime", earliestPlanTime)
            .put("latestPlanTime", latestPlanTime)
            .put("weatherCity", weatherCity)
    }

    private fun AiDayContext.toJson(includeDetails: Boolean): JSONObject {
        val json = JSONObject()
            .put("date", date)
            .put("weekday", weekday)
            .put("weekdayName", weekdayName)
            .put(
                "courses",
                JSONArray().apply { courses.forEach { put(it.toJson(includeDetails)) } }
            )
            .put(
                "occupiedTimeRanges",
                JSONArray().apply { occupiedTimeRanges.forEach { put(it.toJson()) }
                }
            )
        if (includeDetails) {
            json.put(
                "existingPlans",
                JSONArray().apply { existingPlans.forEach { put(it.toJson()) }
                }
            )
            json.put(
                "busyWindows",
                JSONArray().apply { busyWindows.forEach { put(it.toJson()) }
                }
            )
        }
        return json
    }

    private fun AiCourseFact.toJson(includeDetails: Boolean): JSONObject {
        val json = JSONObject()
            .put("ref", localRef)
            .put("name", name)
            .put("startSection", startSection)
            .put("endSection", endSection)
            .put("timeRange", timeRange)
        if (includeDetails) {
            json.put("teacher", teacher.orEmpty())
            json.put("classroom", classroom.orEmpty())
            json.put("startWeek", startWeek)
            json.put("endWeek", endWeek)
            json.put("weekType", weekType)
        }
        return json
    }

    private fun AiTaskFact.toJson(zoneId: String, includeDetails: Boolean): JSONObject {
        val json = JSONObject()
            .put("ref", localRef)
            .put("courseRef", courseRef.orEmpty())
            .put("courseName", courseName.orEmpty())
            .put("title", title)
            .put("type", type)
            .put("priority", priority)
            .put("dueAt", dueAt?.let { formatDateTime(it, zoneId) }.orEmpty())
            .put("overdue", overdue)
        if (includeDetails) {
            json.put("description", description.orEmpty())
            json.put("remindAt", remindAt?.let { formatDateTime(it, zoneId) }.orEmpty())
        }
        return json
    }

    private fun AiPlanFact.toJson(): JSONObject {
        return JSONObject()
            .put("ref", localRef)
            .put("title", title)
            .put("startTime", startTime.orEmpty())
            .put("endTime", endTime.orEmpty())
            .put("plannedMinutes", plannedMinutes)
            .put("actualMinutes", actualMinutes)
            .put("status", status)
    }

    private fun AiBusyWindow.toJson(): JSONObject {
        return JSONObject()
            .put("startTime", startTime)
            .put("endTime", endTime)
            .put("type", type.name.lowercase())
            .put("label", label)
            .put("ref", localRef)
    }

    private fun AiOccupiedTimeRange.toJson(): JSONObject {
        return JSONObject()
            .put("startTime", startTime)
            .put("endTime", endTime)
    }

    private fun AiLearningProgress.toJson(): JSONObject {
        return JSONObject()
            .put(
                "days",
                JSONArray().apply {
                    days.forEach { day ->
                        put(
                            JSONObject()
                                .put("date", day.date)
                                .put("minutes", day.minutes)
                                .put("sessionCount", day.sessionCount)
                        )
                    }
                }
            )
            .put("totalMinutes", totalMinutes)
            .put("averageMinutesPerDay", averageMinutesPerDay)
            .put("activeDays", activeDays)
            .put("currentStreakDays", currentStreakDays)
            .put("goalHitDays", goalHitDays)
            .put("trend", trend.name.lowercase())
    }

    private fun AiWeatherFact.toJson(zoneId: String): JSONObject {
        return JSONObject()
            .put("city", city)
            .put("weatherText", weatherText)
            .put("temperature", temperature)
            .put("humidity", humidity)
            .put("wind", wind)
            .put("source", source)
            .put("updatedAt", formatDateTime(updatedAt, zoneId))
            .put("ageMinutes", ageMillis / 60_000L)
            .put("freshness", freshness.name.lowercase())
            .put("usableForRealtimeAdvice", usableForRealtimeAdvice)
    }

    private fun AiContextWarning.toJson(): JSONObject {
        return JSONObject()
            .put("code", code.name.lowercase())
            .put("message", message)
    }

    private fun AiContextOmissions.toJson(): JSONObject {
        return JSONObject()
            .put("courses", courses)
            .put("tasks", tasks)
            .put("plans", plans)
    }

    private fun formatDateTime(timeMillis: Long, zoneId: String): String {
        return DATE_TIME_FORMATTER.format(
            Instant.ofEpochMilli(timeMillis).atZone(ZoneId.of(zoneId))
        )
    }

    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
}
