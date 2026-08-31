package com.example.campusmate

import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.domain.ai.context.AiLearningTrend
import com.example.campusmate.domain.ai.memory.LearningGrowthSummaryBuilder
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LearningGrowthSummaryBuilderTest {
    private val end = "2026-02-01"
    private fun record(date: String, seconds: Int) = StudyRecord(recordDate = date, durationSec = seconds)
    private fun build(records: List<StudyRecord>) = LearningGrowthSummaryBuilder.build(records, end, 60)

    @Test fun emptyHistoryHasEightContinuousWeeksAcrossYearBoundary() {
        val summary = build(emptyList())
        assertEquals("2025-12-08", summary.rangeStart)
        assertEquals(end, summary.rangeEnd)
        assertEquals(8, summary.weeks.size)
        assertEquals("2025-12-08", summary.weeks.first().rangeStart)
        assertEquals(end, summary.weeks.last().rangeEnd)
        assertEquals(0, summary.totalMinutes)
        assertEquals(AiLearningTrend.INSUFFICIENT_DATA, summary.trend)
        summary.weeks.zipWithNext().forEach { (left, right) ->
            assertEquals(LocalDate.parse(left.rangeEnd).plusDays(1).toString(), right.rangeStart)
        }
    }

    @Test fun sumsSecondsPerDayBeforeRoundingAndDoesNotReadTitlesOrNotes() {
        val summary = build(listOf(record(end, 30), record(end, 30), record(end, 3_600)))
        assertEquals(61, summary.totalMinutes)
        assertEquals(3, summary.sessionCount)
        assertEquals(1, summary.activeDays)
        assertEquals(1, summary.goalHitDays)
        assertEquals(1, summary.currentStreakDays)
        assertEquals(61, summary.weeks.last().minutes)
    }

    @Test fun excludesFutureOldMalformedAndNonPositiveRecords() {
        val summary = build(listOf(
            record("2026-02-02", 3_600), record("2025-12-07", 3_600),
            record("2026-01-32", 3_600), record(end, -1), record(end, 0),
            record("2025-12-08", 60), record(end, 120)
        ))
        assertEquals(3, summary.totalMinutes)
        assertEquals(2, summary.sessionCount)
        assertEquals(2, summary.activeDays)
    }

    @Test fun comparesTwoEqual28DayWindowsWithTenPercentThreshold() {
        val cases = listOf(
            Triple(100, 120, AiLearningTrend.UP),
            Triple(100, 80, AiLearningTrend.DOWN),
            Triple(100, 110, AiLearningTrend.STEADY),
            Triple(100, 90, AiLearningTrend.STEADY),
            Triple(0, 60, AiLearningTrend.UP)
        )
        cases.forEach { (previous, recent, trend) ->
            val summary = build(listOf(record("2025-12-08", previous * 60), record(end, recent * 60)))
            assertEquals(previous, summary.previousMinutes)
            assertEquals(recent, summary.recentMinutes)
            assertEquals(trend, summary.trend)
        }
    }

    @Test fun orderingDoesNotAffectWeeklyPathOrStreak() {
        val records = (0..5).map { record(LocalDate.parse(end).minusDays(it.toLong()).toString(), 3_600) }
        assertEquals(build(records), build(records.reversed()))
        assertEquals(6, build(records).currentStreakDays)
        assertEquals(6, build(records).goalHitDays)
    }
}
