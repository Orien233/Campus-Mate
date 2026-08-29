package com.example.campusmate.domain.schedule

import com.example.campusmate.data.model.Course
import com.example.campusmate.data.repository.SettingsSectionTimeSlot

data class CourseTimeRange(
    val displayText: String,
    val startTime: String?,
    val endTime: String?
)

class CourseTimeResolver(
    configuredSlots: List<SettingsSectionTimeSlot>
) {
    private val configuredBySection = configuredSlots.associateBy { it.section }

    fun resolve(course: Course): CourseTimeRange {
        val startTime = configuredBySection[course.startSection]?.startTime
            ?: DEFAULT_SECTION_TIMES[course.startSection]?.first
        val endTime = configuredBySection[course.endSection]?.endTime
            ?: DEFAULT_SECTION_TIMES[course.endSection]?.second
        return if (startTime != null && endTime != null) {
            CourseTimeRange(
                displayText = "$startTime-$endTime",
                startTime = startTime,
                endTime = endTime
            )
        } else {
            CourseTimeRange(
                displayText = "第 ${course.startSection}-${course.endSection} 节",
                startTime = null,
                endTime = null
            )
        }
    }

    companion object {
        private val DEFAULT_SECTION_TIMES = mapOf(
            1 to ("08:00" to "08:45"),
            2 to ("08:50" to "09:35"),
            3 to ("09:50" to "10:35"),
            4 to ("10:40" to "11:25"),
            5 to ("11:30" to "12:15"),
            6 to ("13:30" to "14:15"),
            7 to ("14:20" to "15:05"),
            8 to ("15:20" to "16:05"),
            9 to ("16:10" to "16:55"),
            10 to ("17:00" to "17:45"),
            11 to ("19:00" to "19:45"),
            12 to ("19:50" to "20:35")
        )
    }
}
