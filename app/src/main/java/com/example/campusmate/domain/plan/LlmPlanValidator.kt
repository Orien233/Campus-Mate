package com.example.campusmate.domain.plan

import com.example.campusmate.data.model.StudyPlan
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class LlmPlanValidator {

    data class ValidationResult(
        val plans: List<StudyPlan>,
        val warnings: List<String>
    )

    fun parseAndValidate(jsonContent: String, planDate: String): ValidationResult {
        return parseAndValidate(jsonContent, planDate, null)
    }

    fun parseAndValidate(jsonContent: String, planContext: StudyPlanContext): ValidationResult {
        return parseAndValidate(jsonContent, planContext.date, planContext)
    }

    private fun parseAndValidate(
        jsonContent: String,
        planDate: String,
        planContext: StudyPlanContext?
    ): ValidationResult {
        val warnings = mutableListOf<String>()
        val plans = mutableListOf<StudyPlan>()

        try {
            val json = JSONObject(jsonContent)
            val plansArray = json.optJSONArray("plans") ?: return ValidationResult(emptyList(), listOf("未找到 plans 数组"))

            for (i in 0 until plansArray.length()) {
                val planJson = plansArray.optJSONObject(i) ?: continue

                val title = planJson.optString("title", "").takeIf { it.isNotBlank() }
                if (title == null) {
                    warnings.add("第 ${i + 1} 个计划缺少标题，已跳过")
                    continue
                }

                val plannedMinutes = planJson.optInt("plannedMinutes", 0).takeIf { it in 5..240 }
                if (plannedMinutes == null) {
                    warnings.add("\"$title\" 时长不在合理范围(5-240分钟)")
                    continue
                }

                val startTime = planJson.optString("startTime", "").takeIf { isValidTimeFormat(it) }
                val endTime = planJson.optString("endTime", "").takeIf { isValidTimeFormat(it) }

                if (startTime == null || endTime == null) {
                    warnings.add("\"$title\" 缺少有效开始/结束时间，已跳过")
                    continue
                }

                if (!isEndAfterStart(startTime, endTime)) {
                    warnings.add("\"$title\" 结束时间早于开始时间")
                    continue
                }

                val plan = StudyPlan(
                    title = title,
                    planDate = planDate,
                    plannedMinutes = plannedMinutes,
                    startTime = startTime,
                    endTime = endTime,
                    type = planJson.optInt("type", StudyPlan.TYPE_DAILY),
                    sourceType = StudyPlan.SOURCE_LLM
                )
                val localWarning = planContext?.let { validateAgainstContext(plan, it) }
                if (localWarning != null) {
                    warnings.add(localWarning)
                    continue
                }
                plans.add(plan)
            }

            if (plans.isEmpty()) {
                warnings.add("未生成有效计划")
            }

            return ValidationResult(plans, warnings)

        } catch (e: Exception) {
            return ValidationResult(emptyList(), listOf("JSON 解析失败: ${e.message}"))
        }
    }

    private fun isValidTimeFormat(time: String): Boolean {
        if (time.isBlank()) return false
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.US)
            sdf.isLenient = false
            sdf.parse(time)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun validateAgainstContext(plan: StudyPlan, context: StudyPlanContext): String? {
        val start = parseMinutes(plan.startTime) ?: return "\"${plan.title}\" 缺少有效开始时间，已跳过"
        val end = parseMinutes(plan.endTime) ?: return "\"${plan.title}\" 缺少有效结束时间，已跳过"
        val allowedStart = parseMinutes(context.generationStartTime) ?: parseMinutes(context.planEarliestTime) ?: 0
        val allowedEnd = parseMinutes(context.planLatestTime) ?: 24 * 60
        if (start < allowedStart || end > allowedEnd) {
            return "\"${plan.title}\" 不在允许生成时间 ${context.generationStartTime}-${context.planLatestTime} 内，已跳过"
        }

        for (course in context.courses) {
            val range = context.courseTimeRanges[course.id] ?: continue
            val (courseStart, courseEnd) = parseRange(range) ?: continue
            val overlapsCourse = start < courseEnd && end > courseStart
            val courseLearning = isCourseLearningPlan(plan.title, course.name)
            if (courseLearning && (start < courseStart || end > courseEnd)) {
                return "\"${plan.title}\" 应安排在课程“${course.name}”时间 $range 内，已跳过"
            }
            if (!courseLearning && overlapsCourse) {
                return "\"${plan.title}\" 与课程“${course.name}”时间 $range 冲突，已跳过"
            }
        }
        return null
    }

    private fun isCourseLearningPlan(title: String, courseName: String): Boolean {
        val normalizedCourseName = courseName.trim()
        if (normalizedCourseName.isBlank() || !title.contains(normalizedCourseName, ignoreCase = true)) return false
        return COURSE_LEARNING_KEYWORDS.any { title.contains(it, ignoreCase = true) }
    }

    private fun parseRange(range: String): Pair<Int, Int>? {
        val match = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""").find(range) ?: return null
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

    private fun isEndAfterStart(start: String, end: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.US)
            val startTime = sdf.parse(start)?.time ?: return true
            val endTime = sdf.parse(end)?.time ?: return true
            endTime > startTime
        } catch (e: Exception) {
            true
        }
    }

    companion object {
        private val COURSE_LEARNING_KEYWORDS = listOf("上课", "课程学习", "完成课程学习", "课堂", "听课")
    }
}
