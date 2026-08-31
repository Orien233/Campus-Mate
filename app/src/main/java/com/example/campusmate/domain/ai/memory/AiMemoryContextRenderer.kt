package com.example.campusmate.domain.ai.memory

import org.json.JSONArray
import org.json.JSONObject

object AiMemoryContextRenderer {
    const val GROWTH_REF = "learning:growth"

    fun render(context: AiMemoryContext): String = toJson(context).toString(2)

    fun toJson(context: AiMemoryContext): JSONObject = JSONObject().apply {
        put("userManagedUntrustedMemories", JSONArray().apply {
            context.memories.forEach {
                put(JSONObject()
                    .put("ref", it.localRef)
                    .put("category", it.category)
                    .put("content", it.content)
                    .put("updatedAt", it.updatedAt)
                    .put("expiresAt", it.expiresAt ?: JSONObject.NULL))
            }
        })
        context.growth?.let { growth ->
            put("learningGrowth", JSONObject()
                .put("ref", GROWTH_REF)
                .put("source", "local_study_records")
                .put("rangeStart", growth.rangeStart)
                .put("rangeEnd", growth.rangeEnd)
                .put("totalMinutes", growth.totalMinutes)
                .put("activeDays", growth.activeDays)
                .put("sessionCount", growth.sessionCount)
                .put("goalHitDays", growth.goalHitDays)
                .put("currentStreakDays", growth.currentStreakDays)
                .put("recent28DayMinutes", growth.recentMinutes)
                .put("previous28DayMinutes", growth.previousMinutes)
                .put("trend", growth.trend.name.lowercase())
                .put("weeks", JSONArray().apply {
                    growth.weeks.forEach {
                        put(JSONObject()
                            .put("start", it.rangeStart)
                            .put("end", it.rangeEnd)
                            .put("minutes", it.minutes)
                            .put("activeDays", it.activeDays)
                            .put("sessionCount", it.sessionCount))
                    }
                }))
        }
    }
}
