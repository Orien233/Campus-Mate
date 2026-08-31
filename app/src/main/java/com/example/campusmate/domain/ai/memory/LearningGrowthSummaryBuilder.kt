package com.example.campusmate.domain.ai.memory

import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.domain.ai.context.AiLearningTrend
import java.time.LocalDate

/** Derived facts only: no model call, inferred ability claim, or persisted summary. */
object LearningGrowthSummaryBuilder {
    const val WINDOW_DAYS = 56

    fun build(
        records: List<StudyRecord>,
        anchorDate: String,
        dailyGoalMinutes: Int
    ): LearningGrowthSummary {
        val end = LocalDate.parse(anchorDate)
        val start = end.minusDays(WINDOW_DAYS - 1L)
        val recordsByDate = records.filter {
            it.durationSec > 0 && it.recordDate in start.toString()..end.toString() &&
                runCatching { LocalDate.parse(it.recordDate).toString() == it.recordDate }.getOrDefault(false)
        }.groupBy(StudyRecord::recordDate)
        val days = (0 until WINDOW_DAYS).map { offset ->
            val date = start.plusDays(offset.toLong()).toString()
            val dayRecords = recordsByDate[date].orEmpty()
            Day(date, (dayRecords.sumOf { it.durationSec.toLong() } / 60).toInt(), dayRecords.size)
        }
        val previousMinutes = days.take(28).sumOf(Day::minutes)
        val recentMinutes = days.takeLast(28).sumOf(Day::minutes)
        return LearningGrowthSummary(
            rangeStart = start.toString(),
            rangeEnd = end.toString(),
            totalMinutes = days.sumOf(Day::minutes),
            activeDays = days.count { it.minutes > 0 },
            sessionCount = days.sumOf(Day::sessions),
            goalHitDays = days.count { it.minutes >= dailyGoalMinutes.coerceAtLeast(1) },
            currentStreakDays = days.asReversed().takeWhile { it.minutes > 0 }.size,
            recentMinutes = recentMinutes,
            previousMinutes = previousMinutes,
            trend = when {
                recentMinutes == 0 && previousMinutes == 0 -> AiLearningTrend.INSUFFICIENT_DATA
                previousMinutes == 0 -> AiLearningTrend.UP
                recentMinutes.toLong() * 10 > previousMinutes.toLong() * 11 -> AiLearningTrend.UP
                recentMinutes.toLong() * 10 < previousMinutes.toLong() * 9 -> AiLearningTrend.DOWN
                else -> AiLearningTrend.STEADY
            },
            weeks = days.chunked(7).map { week ->
                LearningGrowthWeek(
                    rangeStart = week.first().date,
                    rangeEnd = week.last().date,
                    minutes = week.sumOf(Day::minutes),
                    activeDays = week.count { it.minutes > 0 },
                    sessionCount = week.sumOf(Day::sessions)
                )
            }
        )
    }

    private data class Day(val date: String, val minutes: Int, val sessions: Int)
}
