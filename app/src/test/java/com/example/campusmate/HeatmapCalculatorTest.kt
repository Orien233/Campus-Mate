package com.example.campusmate

import com.example.campusmate.data.model.DailyStudyStat
import com.example.campusmate.domain.statistics.HeatmapCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapCalculatorTest {
    @Test
    fun intensityForDuration_returnsExpectedLevels() {
        val calculator = HeatmapCalculator()

        assertEquals(0, calculator.intensityForDuration(0))
        assertEquals(1, calculator.intensityForDuration(10 * 60))
        assertEquals(2, calculator.intensityForDuration(45 * 60))
        assertEquals(3, calculator.intensityForDuration(90 * 60))
        assertEquals(4, calculator.intensityForDuration(120 * 60))
    }

    @Test
    fun calculateStreak_countsBackwardsFromEndDate() {
        val calculator = HeatmapCalculator()
        val stats = listOf(
            DailyStudyStat("2026-05-18", 600, 1),
            DailyStudyStat("2026-05-19", 1200, 1),
            DailyStudyStat("2026-05-20", 1800, 1)
        )

        assertEquals(3, calculator.calculateStreak(stats, "2026-05-20"))
        assertEquals(0, calculator.calculateStreak(stats, "2026-05-21"))
    }

    @Test
    fun calculate_fillsMissingDaysWithZeroIntensity() {
        val calculator = HeatmapCalculator()
        val days = calculator.calculate(
            stats = listOf(DailyStudyStat("2026-05-20", 3600, 2)),
            endDate = "2026-05-20",
            dayCount = 3
        )

        assertEquals(listOf("2026-05-18", "2026-05-19", "2026-05-20"), days.map { it.date })
        assertEquals(listOf(0, 0, 3), days.map { it.intensity })
    }
}
