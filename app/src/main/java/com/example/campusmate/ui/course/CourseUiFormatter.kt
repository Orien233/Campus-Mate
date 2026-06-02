package com.example.campusmate.ui.course

import android.content.Context
import android.graphics.Color
import com.example.campusmate.R
import com.example.campusmate.data.model.Course
import com.example.campusmate.data.repository.SettingsRepository

/** Presentation helpers for course fields shared by list and detail screens. */
object CourseUiFormatter {
    private val weekdayResIds = listOf(
        R.string.weekday_monday,
        R.string.weekday_tuesday,
        R.string.weekday_wednesday,
        R.string.weekday_thursday,
        R.string.weekday_friday,
        R.string.weekday_saturday,
        R.string.weekday_sunday
    )

    fun weekdayLabel(context: Context, weekday: Int): String {
        val resId = weekdayResIds.getOrNull(weekday - 1) ?: R.string.course_weekday_unknown
        return context.getString(resId)
    }

    fun sectionRange(context: Context, course: Course): String {
        return context.getString(R.string.course_section_range_format, course.startSection, course.endSection)
    }

    fun weekRange(context: Context, course: Course): String {
        val typeLabel = when (course.weekType) {
            Course.WEEK_TYPE_ODD -> context.getString(R.string.course_week_type_odd)
            Course.WEEK_TYPE_EVEN -> context.getString(R.string.course_week_type_even)
            else -> context.getString(R.string.course_week_type_every)
        }
        return context.getString(R.string.course_week_range_format, course.startWeek, course.endWeek, typeLabel)
    }

    fun timeSummary(context: Context, course: Course): String {
        val weekday = weekdayLabel(context, course.weekday)
        val section = sectionRange(context, course)
        val clock = clockTimeRange(context, course)?.let { " $it" }.orEmpty()
        val week = weekRange(context, course)
        return context.getString(R.string.course_time_summary_format, weekday, section, clock, week)
    }

    fun teacherAndRoom(context: Context, course: Course): String {
        val parts = listOfNotNull(course.teacher?.takeIf { it.isNotBlank() }, course.classroom?.takeIf { it.isNotBlank() })
        return parts.joinToString(" · ").ifBlank { context.getString(R.string.course_teacher_room_empty) }
    }

    private fun clockTimeRange(context: Context, course: Course): String? {
        val slots = SettingsRepository(context).getSectionTimeSlots()
        if (slots.isEmpty()) return null
        val start = slots.firstOrNull { it.section == course.startSection } ?: return null
        val end = slots.firstOrNull { it.section == course.endSection } ?: return null
        return context.getString(R.string.course_clock_time_format, start.startTime, end.endTime)
    }

    fun parseColorOrDefault(value: String?): Int {
        return try {
            Color.parseColor(value ?: DEFAULT_COLOR)
        } catch (_: IllegalArgumentException) {
            Color.parseColor(DEFAULT_COLOR)
        }
    }

    private const val DEFAULT_COLOR = "#1B6B5F"
}

