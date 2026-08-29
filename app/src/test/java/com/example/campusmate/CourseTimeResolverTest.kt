package com.example.campusmate

import com.example.campusmate.data.model.Course
import com.example.campusmate.data.repository.SettingsSectionTimeSlot
import com.example.campusmate.domain.schedule.CourseTimeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseTimeResolverTest {
    @Test
    fun resolve_prefersConfiguredSectionBoundaries() {
        val resolver = CourseTimeResolver(
            listOf(
                SettingsSectionTimeSlot(1, "07:30", "08:15"),
                SettingsSectionTimeSlot(2, "08:20", "09:05")
            )
        )

        val result = resolver.resolve(course(startSection = 1, endSection = 2))

        assertEquals("07:30-09:05", result.displayText)
        assertEquals("07:30", result.startTime)
        assertEquals("09:05", result.endTime)
    }

    @Test
    fun resolve_usesDefaultsAndFallsBackToSectionLabels() {
        val resolver = CourseTimeResolver(emptyList())

        assertEquals("09:50-11:25", resolver.resolve(course(3, 4)).displayText)
        val unknown = resolver.resolve(course(20, 21))
        assertEquals("第 20-21 节", unknown.displayText)
        assertNull(unknown.startTime)
        assertNull(unknown.endTime)
    }

    private fun course(startSection: Int, endSection: Int): Course {
        return Course(
            id = 1L,
            name = "测试课程",
            weekday = 1,
            startSection = startSection,
            endSection = endSection
        )
    }
}
