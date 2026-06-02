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

                if (startTime != null && endTime != null) {
                    if (!isEndAfterStart(startTime, endTime)) {
                        warnings.add("\"$title\" 结束时间早于开始时间")
                        continue
                    }
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
}
