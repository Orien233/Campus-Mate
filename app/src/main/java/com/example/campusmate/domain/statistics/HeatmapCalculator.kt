package com.example.campusmate.domain.statistics

import com.example.campusmate.data.model.DailyStudyStat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Calculates heatmap cells, intensity levels, and study streaks from daily stats. */
class HeatmapCalculator {
    fun calculate(
        stats: List<DailyStudyStat>,
        endDate: String,
        dayCount: Int = DEFAULT_DAY_COUNT
    ): List<HeatmapDay> {
        val safeDayCount = dayCount.coerceAtLeast(1)
        val end = LocalDate.parse(endDate, formatter)
        val statByDate = stats.associateBy { it.recordDate }
        return (safeDayCount - 1 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong())
            val dateText = date.format(formatter)
            val stat = statByDate[dateText]
            val durationSec = stat?.durationSec ?: 0
            HeatmapDay(
                date = dateText,
                durationSec = durationSec,
                recordCount = stat?.recordCount ?: 0,
                intensity = intensityForDuration(durationSec),
                isToday = offset == 0
            )
        }
    }

    fun calculateStreak(stats: List<DailyStudyStat>, endDate: String): Int {
        val studiedDates = stats.filter { it.durationSec > 0 }.map { it.recordDate }.toSet()
        var cursor = LocalDate.parse(endDate, formatter)
        var streak = 0
        while (studiedDates.contains(cursor.format(formatter))) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun intensityForDuration(durationSec: Int): Int {
        val minutes = durationSec / 60
        return when {
            minutes <= 0 -> 0
            minutes < 30 -> 1
            minutes < 60 -> 2
            minutes < 120 -> 3
            else -> 4
        }
    }

    companion object {
        const val DEFAULT_DAY_COUNT = 84
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

data class HeatmapDay(
    val date: String,
    val durationSec: Int,
    val recordCount: Int,
    val intensity: Int,
    val isToday: Boolean
)
