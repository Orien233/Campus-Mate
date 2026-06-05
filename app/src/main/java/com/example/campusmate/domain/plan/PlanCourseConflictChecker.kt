package com.example.campusmate.domain.plan

import com.example.campusmate.data.model.StudyPlan

data class PlanCourseConflict(
    val planTitle: String,
    val courseName: String,
    val courseTimeRange: String
)

object PlanCourseConflictChecker {
    fun findConflicts(plans: List<StudyPlan>, context: StudyPlanContext): List<PlanCourseConflict> {
        return plans.flatMap { plan ->
            val planStart = parseMinutes(plan.startTime) ?: return@flatMap emptyList()
            val planEnd = parseMinutes(plan.endTime) ?: return@flatMap emptyList()
            if (planEnd <= planStart) return@flatMap emptyList()
            context.courses.mapNotNull { course ->
                val range = context.courseTimeRanges[course.id] ?: return@mapNotNull null
                val (courseStart, courseEnd) = parseRange(range) ?: return@mapNotNull null
                if (
                    planStart < courseEnd &&
                    planEnd > courseStart &&
                    !isCourseLearningPlan(plan, course)
                ) {
                    PlanCourseConflict(
                        planTitle = plan.title,
                        courseName = course.name,
                        courseTimeRange = range
                    )
                } else {
                    null
                }
            }
        }
    }

    fun courseBusySummary(context: StudyPlanContext): List<String> {
        return context.courses.map { course ->
            val range = context.courseTimeRanges[course.id] ?: "第 ${course.startSection}-${course.endSection} 节"
            "${context.weekdayName} $range ${course.name}"
        }
    }

    private fun isCourseLearningPlan(plan: StudyPlan, course: com.example.campusmate.data.model.Course): Boolean {
        val title = plan.title.trim()
        val courseName = course.name.trim()
        if (courseName.isBlank() || !title.contains(courseName, ignoreCase = true)) return false
        return listOf("上课", "课程学习", "完成课程学习", "课堂", "听课").any { keyword ->
            title.contains(keyword, ignoreCase = true)
        }
    }

    private fun parseRange(range: String): Pair<Int, Int>? {
        val match = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""").find(range)
            ?: return null
        val start = toMinutes(match.groupValues[1], match.groupValues[2]) ?: return null
        val end = toMinutes(match.groupValues[3], match.groupValues[4]) ?: return null
        return start to end
    }

    private fun parseMinutes(value: String?): Int? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(value?.trim().orEmpty()) ?: return null
        return toMinutes(match.groupValues[1], match.groupValues[2])
    }

    private fun toMinutes(hourText: String, minuteText: String): Int? {
        val hour = hourText.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = minuteText.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }
}
