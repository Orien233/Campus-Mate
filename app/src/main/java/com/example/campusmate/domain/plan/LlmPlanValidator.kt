package com.example.campusmate.domain.plan

import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.domain.llm.LlmJsonPayloadExtractor
import org.json.JSONObject

class LlmPlanValidator {

    data class ValidationResult(
        val plans: List<StudyPlan>,
        val warnings: List<String>
    )

    fun parseAndValidate(
        jsonContent: String,
        planDate: String,
        outputPlanType: Int = StudyPlan.TYPE_DAILY
    ): ValidationResult {
        return parseAndValidate(jsonContent, planDate, null, outputPlanType)
    }

    fun parseAndValidate(
        jsonContent: String,
        planContext: StudyPlanContext,
        outputPlanType: Int = StudyPlan.TYPE_DAILY
    ): ValidationResult {
        return parseAndValidate(jsonContent, planContext.date, planContext, outputPlanType)
    }

    private fun parseAndValidate(
        jsonContent: String,
        planDate: String,
        planContext: StudyPlanContext?,
        outputPlanType: Int
    ): ValidationResult {
        require(outputPlanType == StudyPlan.TYPE_DAILY || outputPlanType == StudyPlan.TYPE_WEEKLY) {
            "Unsupported plan type: $outputPlanType"
        }
        val warnings = mutableListOf<String>()
        val plans = mutableListOf<StudyPlan>()

        try {
            val jsonText = LlmJsonPayloadExtractor.extractObject(jsonContent)
                ?: return ValidationResult(emptyList(), listOf("未找到有效 JSON 对象"))
            val json = JSONObject(jsonText)
            warnings += readWarnings(json)
            val plansArray = json.optJSONArray("plans") ?: return ValidationResult(emptyList(), listOf("未找到 plans 数组"))

            for (i in 0 until plansArray.length()) {
                val planJson = plansArray.optJSONObject(i) ?: continue
                if (!hasValidMemoryRefs(planJson, planContext?.allowedMemoryRefs.orEmpty())) {
                    warnings.add("第 ${i + 1} 个计划包含无效或未检索的记忆引用，已跳过")
                    continue
                }

                val title = planJson.optString("title", "").takeIf { it.isNotBlank() }
                if (title == null) {
                    warnings.add("第 ${i + 1} 个计划缺少标题，已跳过")
                    continue
                }

                val plannedMinutes = (planJson.opt("plannedMinutes") as? Number)
                    ?.toDouble()
                    ?.takeIf { it in 5.0..240.0 && it % 1.0 == 0.0 }
                    ?.toInt()
                if (plannedMinutes == null) {
                    warnings.add("\"$title\" 时长不在合理范围(5-240分钟)")
                    continue
                }

                val startTime = planJson.optString("startTime", "").trim()
                val endTime = planJson.optString("endTime", "").trim()
                val start = parseMinutes(startTime)
                val end = parseMinutes(endTime)

                if (start == null || end == null) {
                    warnings.add("\"$title\" 缺少有效开始/结束时间，已跳过")
                    continue
                }

                if (end <= start) {
                    warnings.add("\"$title\" 结束时间早于开始时间")
                    continue
                }
                if (plannedMinutes != end - start) {
                    warnings.add("\"$title\" 时长与开始结束时间不一致，已跳过")
                    continue
                }

                val plan = StudyPlan(
                    title = title,
                    planDate = planDate,
                    plannedMinutes = plannedMinutes,
                    startTime = startTime,
                    endTime = endTime,
                    type = outputPlanType,
                    sourceType = StudyPlan.SOURCE_LLM
                )
                val localWarning = planContext?.let { validateAgainstContext(plan, it) }
                if (localWarning != null) {
                    warnings.add(localWarning)
                    continue
                }
                if (plans.any { overlaps(it, start, end) }) {
                    warnings.add("\"$title\" 与本次其他计划时间冲突，已跳过")
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

    private fun readWarnings(root: JSONObject): List<String> {
        val values = root.optJSONArray("warnings") ?: return emptyList()
        return (0 until minOf(values.length(), MAX_MODEL_WARNINGS))
            .mapNotNull { index ->
                values.optString(index, "").trim().takeIf(String::isNotBlank)?.take(MAX_WARNING_CHARS)
            }
            .distinct()
    }

    private fun hasValidMemoryRefs(plan: JSONObject, allowedRefs: Set<String>): Boolean {
        if (!plan.has("memoryRefs")) return true
        val refs = plan.optJSONArray("memoryRefs") ?: return false
        return (0 until refs.length()).all { index ->
            val ref = refs.opt(index)
            ref is String && ref in allowedRefs
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
        if (context.existingPlans.any { it.planDate == context.date && overlaps(it, start, end) }) {
            return "\"${plan.title}\" 与已有计划时间冲突，已跳过"
        }
        return null
    }

    private fun overlaps(plan: StudyPlan, start: Int, end: Int): Boolean {
        val occupiedStart = parseMinutes(plan.startTime) ?: return false
        val occupiedEnd = parseMinutes(plan.endTime) ?: return false
        return occupiedEnd > occupiedStart && start < occupiedEnd && end > occupiedStart
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
        val match = TIME_PATTERN.matchEntire(value?.trim().orEmpty()) ?: return null
        return toMinutes(match.groupValues[1], match.groupValues[2])
    }

    private fun toMinutes(hourText: String, minuteText: String): Int? {
        val hour = hourText.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = minuteText.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }

    companion object {
        private val TIME_PATTERN = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")
        private val COURSE_LEARNING_KEYWORDS = listOf("上课", "课程学习", "完成课程学习", "课堂", "听课")
        private const val MAX_MODEL_WARNINGS = 8
        private const val MAX_WARNING_CHARS = 240
    }
}
