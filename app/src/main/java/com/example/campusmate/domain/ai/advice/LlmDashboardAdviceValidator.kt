package com.example.campusmate.domain.ai.advice

import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.domain.llm.LlmJsonPayloadExtractor
import org.json.JSONArray
import org.json.JSONObject

class LlmDashboardAdviceValidator {
    data class ValidationResult(
        val advice: AiDashboardAdvice?,
        val warnings: List<String>
    )

    fun parseAndValidate(
        rawText: String,
        snapshot: AiContextSnapshot
    ): ValidationResult {
        val jsonText = LlmJsonPayloadExtractor.extractObject(rawText)
            ?: return ValidationResult(null, listOf("未找到有效 JSON 对象"))
        return runCatching {
            validateObject(JSONObject(jsonText), snapshot)
        }.getOrElse {
            ValidationResult(null, listOf("建议 JSON 解析失败"))
        }
    }

    private fun validateObject(
        root: JSONObject,
        snapshot: AiContextSnapshot
    ): ValidationResult {
        val validationWarnings = mutableListOf<String>()
        val headline = normalizedText(root.optString("headline"), MAX_HEADLINE_CHARS)
        val summary = normalizedText(root.optString("summary"), MAX_SUMMARY_CHARS)
        if (headline.isBlank() || summary.isBlank()) {
            return ValidationResult(null, listOf("建议缺少标题或摘要"))
        }

        val allowedRefs = DashboardAdviceContextPolicy.allowedEvidenceRefs(snapshot)
        val validDates = snapshot.days.mapTo(linkedSetOf()) { it.date }
        val occupiedByDate = snapshot.days.associate { day ->
            day.date to day.occupiedTimeRanges.mapNotNull { range ->
                val start = parseMinutes(range.startTime) ?: return@mapNotNull null
                val end = parseMinutes(range.endTime) ?: return@mapNotNull null
                start to end
            }
        }
        val itemsArray = root.optJSONArray("items")
            ?: return ValidationResult(null, listOf("建议缺少 items 数组"))
        val items = mutableListOf<AiDashboardAdviceItem>()
        val deduplicationKeys = mutableSetOf<String>()

        for (index in 0 until minOf(itemsArray.length(), MAX_SCANNED_ITEMS)) {
            val itemJson = itemsArray.optJSONObject(index) ?: continue
            val title = normalizedText(itemJson.optString("title"), MAX_ITEM_TITLE_CHARS)
            val detail = normalizedText(itemJson.optString("detail"), MAX_ITEM_DETAIL_CHARS)
            if (title.isBlank() || detail.isBlank()) {
                validationWarnings += "第 " + (index + 1) + " 条建议缺少标题或说明，已忽略"
                continue
            }

            val priorityText = itemJson.optString("priority")
            val priority = AiAdvicePriority.fromWireValue(priorityText)
                ?: AiAdvicePriority.NORMAL.also {
                    validationWarnings += "“$title”的优先级无效，已按普通处理"
                }
            val evidenceRefs = readStrings(itemJson.optJSONArray("evidenceRefs"), MAX_SCANNED_STRINGS)
                .filter(allowedRefs::contains)
                .take(MAX_EVIDENCE_REFS)
            if (evidenceRefs.isEmpty()) {
                validationWarnings += "“$title”没有可核验的本地依据，已忽略"
                continue
            }

            val timing = validateTiming(
                title = title,
                suggestedDate = itemJson.optString("suggestedDate").trim().takeIf(String::isNotBlank),
                startTime = itemJson.optString("startTime").trim().takeIf(String::isNotBlank),
                endTime = itemJson.optString("endTime").trim().takeIf(String::isNotBlank),
                validDates = validDates,
                occupiedByDate = occupiedByDate,
                warnings = validationWarnings
            )
            val deduplicationKey = "$title\u0000$detail".lowercase()
            if (!deduplicationKeys.add(deduplicationKey)) continue

            items += AiDashboardAdviceItem(
                title = title,
                detail = detail,
                priority = priority,
                suggestedDate = timing.date,
                startTime = timing.startTime,
                endTime = timing.endTime,
                evidenceRefs = evidenceRefs
            )
            if (items.size == MAX_ADVICE_ITEMS) break
        }

        if (itemsArray.length() > MAX_ADVICE_ITEMS) {
            validationWarnings += "模型返回超过三条建议，仅保留前三条有效建议"
        }
        if (items.isEmpty()) {
            return ValidationResult(null, validationWarnings + "没有可展示的有效建议")
        }

        val warnings = (
            readStrings(root.optJSONArray("warnings"), MAX_MODEL_WARNINGS)
                .map { normalizedText(it, MAX_WARNING_CHARS) }
                .filter(String::isNotBlank) +
                validationWarnings
            )
            .distinct()
            .take(MAX_COMBINED_WARNINGS)

        return ValidationResult(
            advice = AiDashboardAdvice(
                headline = headline,
                summary = summary,
                items = items,
                warnings = warnings
            ),
            warnings = validationWarnings.distinct()
        )
    }

    private fun validateTiming(
        title: String,
        suggestedDate: String?,
        startTime: String?,
        endTime: String?,
        validDates: Set<String>,
        occupiedByDate: Map<String, List<Pair<Int, Int>>>,
        warnings: MutableList<String>
    ): ValidatedTiming {
        if (suggestedDate == null && startTime == null && endTime == null) {
            return ValidatedTiming()
        }
        if (suggestedDate == null || suggestedDate !in validDates) {
            warnings += "“$title”的建议日期无效，已移除具体时间"
            return ValidatedTiming()
        }
        if (startTime == null && endTime == null) {
            return ValidatedTiming(date = suggestedDate)
        }
        val start = parseMinutes(startTime)
        val end = parseMinutes(endTime)
        if (start == null || end == null || end <= start) {
            warnings += "“$title”的建议时间无效，已移除具体时间"
            return ValidatedTiming(date = suggestedDate)
        }
        val overlaps = occupiedByDate[suggestedDate].orEmpty().any { (occupiedStart, occupiedEnd) ->
            start < occupiedEnd && end > occupiedStart
        }
        if (overlaps) {
            warnings += "“$title”的建议时间与已有安排冲突，已移除具体时间"
            return ValidatedTiming(date = suggestedDate)
        }
        return ValidatedTiming(
            date = suggestedDate,
            startTime = normalizeTime(requireNotNull(startTime)),
            endTime = normalizeTime(requireNotNull(endTime))
        )
    }

    private fun readStrings(array: JSONArray?, limit: Int): List<String> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), MAX_SCANNED_STRINGS))
            .mapNotNull { index -> array.optString(index).trim().takeIf(String::isNotBlank) }
            .distinct()
            .take(limit)
    }

    private fun normalizedText(value: String, maxChars: Int): String {
        return value.trim().replace(WHITESPACE, " ").take(maxChars)
    }

    private fun parseMinutes(value: String?): Int? {
        val match = TIME_PATTERN.matchEntire(value.orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }

    private fun normalizeTime(value: String): String {
        val minutes = requireNotNull(parseMinutes(value))
        return "%02d:%02d".format(minutes / 60, minutes % 60)
    }

    private data class ValidatedTiming(
        val date: String? = null,
        val startTime: String? = null,
        val endTime: String? = null
    )

    companion object {
        private val WHITESPACE = Regex("""\s+""")
        private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")
        private const val MAX_HEADLINE_CHARS = 60
        private const val MAX_SUMMARY_CHARS = 240
        private const val MAX_ITEM_TITLE_CHARS = 50
        private const val MAX_ITEM_DETAIL_CHARS = 180
        private const val MAX_WARNING_CHARS = 160
        private const val MAX_ADVICE_ITEMS = 3
        private const val MAX_SCANNED_ITEMS = 12
        private const val MAX_EVIDENCE_REFS = 4
        private const val MAX_MODEL_WARNINGS = 6
        private const val MAX_COMBINED_WARNINGS = 6
        private const val MAX_SCANNED_STRINGS = 24
    }
}
