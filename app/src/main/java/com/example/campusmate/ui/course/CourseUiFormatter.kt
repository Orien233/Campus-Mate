package com.example.campusmate.ui.course

import android.graphics.Color
import com.example.campusmate.data.model.Course

/** Presentation helpers for course fields shared by list and detail screens. */
object CourseUiFormatter {
    private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekdayLabel(weekday: Int): String {
        return weekdayLabels.getOrElse(weekday - 1) { "未知" }
    }

    fun sectionRange(course: Course): String {
        return "第${course.startSection}-${course.endSection}节"
    }

    fun weekRange(course: Course): String {
        val typeLabel = when (course.weekType) {
            Course.WEEK_TYPE_ODD -> "单周"
            Course.WEEK_TYPE_EVEN -> "双周"
            else -> "每周"
        }
        return "第${course.startWeek}-${course.endWeek}周 $typeLabel"
    }

    fun timeSummary(course: Course): String {
        return "${weekdayLabel(course.weekday)} ${sectionRange(course)} · ${weekRange(course)}"
    }

    fun teacherAndRoom(course: Course): String {
        val parts = listOfNotNull(course.teacher?.takeIf { it.isNotBlank() }, course.classroom?.takeIf { it.isNotBlank() })
        return parts.joinToString(" · ").ifBlank { "未填写教师和教室" }
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
