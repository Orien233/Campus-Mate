package com.example.campusmate.domain.ai.memory

import com.example.campusmate.domain.ai.context.AiLearningTrend

data class AiMemoryFact(
    val localRef: String,
    val category: String,
    val content: String,
    val updatedAt: Long,
    val expiresAt: Long?
)

data class AiMemoryContext(
    val memories: List<AiMemoryFact> = emptyList(),
    val growth: LearningGrowthSummary? = null
) {
    val allowedMemoryRefs: Set<String>
        get() = memories.mapTo(linkedSetOf()) { it.localRef }
}

data class LearningGrowthWeek(
    val rangeStart: String,
    val rangeEnd: String,
    val minutes: Int,
    val activeDays: Int,
    val sessionCount: Int
)

data class LearningGrowthSummary(
    val rangeStart: String,
    val rangeEnd: String,
    val totalMinutes: Int,
    val activeDays: Int,
    val sessionCount: Int,
    val goalHitDays: Int,
    val currentStreakDays: Int,
    val recentMinutes: Int,
    val previousMinutes: Int,
    val trend: AiLearningTrend,
    val weeks: List<LearningGrowthWeek>
)
