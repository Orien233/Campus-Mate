package com.example.campusmate.domain.ai.memory

import android.content.Context
import com.example.campusmate.data.repository.AiMemoryRepository
import com.example.campusmate.data.repository.SettingsRepository
import com.example.campusmate.data.repository.StudyRecordRepository
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.util.DateTimeUtils

/** Read-only opt-in enrichment shared by explicit AI planning requests. */
class AiMemoryContextProvider(context: Context) {
    private val settings = SettingsRepository(context)
    private val memories = AiMemoryRepository(context)
    private val records = StudyRecordRepository(context)

    fun build(
        purpose: AiContextPurpose,
        queryTerms: List<String>,
        anchorDate: String,
        dailyGoalMinutes: Int,
        limit: Int = 4
    ): AiMemoryContext {
        if (purpose == AiContextPurpose.FILE_ANALYSIS || !settings.isAiMemoryEnabled()) {
            return AiMemoryContext()
        }
        val now = DateTimeUtils.nowMillis()
        val historyEnd = minOf(anchorDate, DateTimeUtils.formatDate(now))
        val historyStart = DateTimeUtils.datePlusDays(historyEnd, -(LearningGrowthSummaryBuilder.WINDOW_DAYS - 1))
        val growth = LearningGrowthSummaryBuilder.build(
            records.getRecordsBetween(historyStart, historyEnd),
            historyEnd,
            dailyGoalMinutes
        )
        return AiMemoryContext(
            memories = AiMemoryRetriever.retrieve(
                memories.getActiveMemories(now), purpose, queryTerms, now, limit
            ),
            growth = growth.takeIf { it.sessionCount > 0 }
        )
    }
}
