package com.example.campusmate.domain.ai.advice

import com.example.campusmate.domain.ai.context.AiContextJsonRenderer
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import java.security.MessageDigest
import com.example.campusmate.domain.ai.memory.AiMemoryContextRenderer

object DashboardAdviceContextPolicy {
    const val DAILY_GOAL_REF = "settings:daily-goal"
    const val RECENT_LEARNING_REF = "learning:recent"
    const val CURRENT_WEATHER_REF = "weather:current"
    const val OCCUPIED_TIME_REF = "schedule:occupied"

    fun allowedEvidenceRefs(snapshot: AiContextSnapshot): Set<String> {
        return linkedSetOf<String>().apply {
            addAll(snapshot.allowedLocalRefs.sorted())
            addAll(snapshot.allowedMemoryRefs.sorted())
            if (snapshot.memoryContext.growth?.sessionCount?.let { it > 0 } == true) {
                add(AiMemoryContextRenderer.GROWTH_REF)
            }
            add(DAILY_GOAL_REF)
            if (snapshot.learningProgress.activeDays > 0) {
                add(RECENT_LEARNING_REF)
            }
            if (snapshot.weather?.usableForRealtimeAdvice == true) {
                add(CURRENT_WEATHER_REF)
            }
            if (snapshot.days.any { it.occupiedTimeRanges.isNotEmpty() }) {
                add(OCCUPIED_TIME_REF)
            }
        }
    }

    fun fingerprint(snapshot: AiContextSnapshot): String {
        val stableSnapshot = snapshot.copy(
            generatedAt = 0L,
            weather = snapshot.weather?.copy(ageMillis = 0L)
        )
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(AiContextJsonRenderer.render(stableSnapshot).toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
