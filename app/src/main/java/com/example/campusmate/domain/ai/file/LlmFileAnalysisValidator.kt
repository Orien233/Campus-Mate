package com.example.campusmate.domain.ai.file

import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.domain.import_.LlmCourseDraftValidator
import com.example.campusmate.domain.task.LlmTaskDraftValidator
import com.example.campusmate.domain.llm.LlmJsonPayloadExtractor
import org.json.JSONArray
import org.json.JSONObject

class LlmFileAnalysisValidator(
    private val courseValidator: LlmCourseDraftValidator = LlmCourseDraftValidator(),
    private val taskValidatorFactory: (Long) -> LlmTaskDraftValidator = { generatedAt ->
        LlmTaskDraftValidator { generatedAt }
    }
) {
    data class ValidationResult(
        val analysis: AiFileAnalysis?,
        val warnings: List<String>
    )

    fun parseAndValidate(rawText: String, snapshot: AiContextSnapshot): ValidationResult {
        if (snapshot.purpose != AiContextPurpose.FILE_ANALYSIS) {
            return ValidationResult(null, listOf("文件分析上下文用途无效"))
        }
        val jsonText = LlmJsonPayloadExtractor.extractObject(rawText)
            ?: return ValidationResult(null, listOf("未找到有效 JSON 对象"))
        return runCatching { validateObject(JSONObject(jsonText), snapshot) }
            .getOrElse { ValidationResult(null, listOf("文件分析 JSON 解析失败")) }
    }

    private fun validateObject(root: JSONObject, snapshot: AiContextSnapshot): ValidationResult {
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            return ValidationResult(null, listOf("文件分析 schemaVersion 不受支持"))
        }
        val summary = normalizedText(root.optString("summary"), MAX_SUMMARY_CHARS)
        if (summary.isBlank()) {
            return ValidationResult(null, listOf("文件分析缺少摘要"))
        }

        val validationWarnings = mutableListOf<String>()
        val allowedLocalRefs = snapshot.allowedLocalRefs
        val courses = validateCourses(root.optJSONArray("courses"), allowedLocalRefs, validationWarnings)
        val tasks = validateTasks(
            root.optJSONArray("tasks"),
            allowedLocalRefs,
            taskValidatorFactory(snapshot.generatedAt),
            validationWarnings
        )
        val plans = validatePlans(root.optJSONArray("plans"), snapshot, validationWarnings)
        val insights = validateInsights(root.optJSONArray("insights"), allowedLocalRefs, validationWarnings)
        val localMatches = validateLocalMatches(
            root.optJSONArray("localMatches"),
            allowedLocalRefs,
            validationWarnings
        )
        val warnings = (
            readStrings(root.optJSONArray("warnings"), MAX_MODEL_WARNINGS)
                .map { normalizedText(it, MAX_WARNING_CHARS) } + validationWarnings
            )
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_COMBINED_WARNINGS)

        return ValidationResult(
            analysis = AiFileAnalysis(
                summary = summary,
                keyPoints = readStrings(root.optJSONArray("keyPoints"), MAX_KEY_POINTS)
                    .map { normalizedText(it, MAX_KEY_POINT_CHARS) }
                    .filter(String::isNotBlank),
                courses = courses,
                tasks = tasks,
                plans = plans,
                insights = insights,
                localMatches = localMatches,
                warnings = warnings
            ),
            warnings = validationWarnings.distinct()
        )
    }

    private fun validateCourses(
        array: JSONArray?,
        allowedLocalRefs: Set<String>,
        warnings: MutableList<String>
    ): List<AiFileCourseCandidate> {
        if (array == null) return emptyList()
        val results = mutableListOf<AiFileCourseCandidate>()
        val keys = mutableSetOf<String>()
        for (index in 0 until minOf(array.length(), MAX_SCANNED_ITEMS)) {
            val item = array.optJSONObject(index) ?: continue
            val evidenceRefs = validatedEvidenceRefs(item, allowedLocalRefs)
            if (AI_FILE_SELECTION_REF !in evidenceRefs) {
                warnings += "第 ${index + 1} 条课程缺少文件依据，已忽略"
                continue
            }
            if (hasFractionalNumber(
                    item,
                    "weekday", "startSection", "endSection", "startWeek", "endWeek", "weekType"
                )
            ) {
                warnings += "第 ${index + 1} 条课程包含非整数数值，已忽略"
                continue
            }
            val sanitized = copyFields(
                item,
                "name", "weekday", "startSection", "endSection",
                "startWeek", "endWeek", "weekType"
            )
            val validationAttempt = runCatching {
                courseValidator.parse(
                    JSONObject().put("courses", JSONArray().put(sanitized)).toString()
                )
            }
            if (validationAttempt.isFailure) {
                warnings += "第 ${index + 1} 条课程字段不完整或无效，已忽略"
                continue
            }
            val validation = validationAttempt.getOrThrow()
            validation.warnings.forEach { warnings += "课程 ${index + 1}：$it" }
            val draft = validation.drafts.single().copy(
                name = normalizedText(validation.drafts.single().name, MAX_NAME_CHARS),
                teacher = null,
                classroom = null,
                note = null,
                sourceText = null
            )
            if (draft.name.isBlank()) {
                warnings += "第 ${index + 1} 条课程名称无效，已忽略"
                continue
            }
            val key = listOf(
                draft.name, draft.weekday, draft.startSection, draft.endSection,
                draft.startWeek, draft.endWeek, draft.weekType
            ).joinToString("|").lowercase()
            if (keys.add(key)) results += AiFileCourseCandidate(draft, evidenceRefs)
            if (results.size == MAX_CATEGORY_ITEMS) break
        }
        appendOverflowWarning("课程", array, warnings)
        return results
    }

    private fun validateTasks(
        array: JSONArray?,
        allowedLocalRefs: Set<String>,
        taskValidator: LlmTaskDraftValidator,
        warnings: MutableList<String>
    ): List<AiFileTaskCandidate> {
        if (array == null) return emptyList()
        val results = mutableListOf<AiFileTaskCandidate>()
        val keys = mutableSetOf<String>()
        for (index in 0 until minOf(array.length(), MAX_SCANNED_ITEMS)) {
            val item = array.optJSONObject(index) ?: continue
            val evidenceRefs = validatedEvidenceRefs(item, allowedLocalRefs)
            if (AI_FILE_SELECTION_REF !in evidenceRefs) {
                warnings += "第 ${index + 1} 条任务缺少文件依据，已忽略"
                continue
            }
            if (hasFractionalNumber(item, "type", "priority", "dueAt", "remindAt")) {
                warnings += "第 ${index + 1} 条任务包含非整数数值，已忽略"
                continue
            }
            val sanitized = copyFields(
                item,
                "title", "courseName", "type", "priority", "dueAt", "remindAt"
            )
            removeInvalidDateTime(sanitized, "dueAt", index, warnings)
            removeInvalidDateTime(sanitized, "remindAt", index, warnings)
            val validationAttempt = runCatching {
                taskValidator.parse(
                    JSONObject().put("tasks", JSONArray().put(sanitized)).toString()
                )
            }
            if (validationAttempt.isFailure) {
                warnings += "第 ${index + 1} 条任务字段不完整或无效，已忽略"
                continue
            }
            val validation = validationAttempt.getOrThrow()
            validation.warnings.forEach { warnings += "任务 ${index + 1}：$it" }
            val parsed = validation.drafts.single()
            val draft = parsed.copy(
                title = normalizedText(parsed.title, MAX_NAME_CHARS),
                description = null,
                courseName = parsed.courseName?.let { normalizedText(it, MAX_NAME_CHARS) }
                    ?.takeIf(String::isNotBlank),
                sourceText = null
            )
            if (draft.title.isBlank()) {
                warnings += "第 ${index + 1} 条任务标题无效，已忽略"
                continue
            }
            if (draft.remindAt != null && draft.dueAt != null && draft.remindAt > draft.dueAt) {
                warnings += "任务“${draft.title}”的提醒晚于截止时间，提醒已移除"
            }
            val safeDraft = if (
                draft.remindAt != null && draft.dueAt != null && draft.remindAt > draft.dueAt
            ) {
                draft.copy(remindAt = null)
            } else {
                draft
            }
            val key = listOf(
                safeDraft.title, safeDraft.courseName.orEmpty(), safeDraft.type,
                safeDraft.priority, safeDraft.dueAt ?: 0L, safeDraft.remindAt ?: 0L
            ).joinToString("|").lowercase()
            if (keys.add(key)) results += AiFileTaskCandidate(safeDraft, evidenceRefs)
            if (results.size == MAX_CATEGORY_ITEMS) break
        }
        appendOverflowWarning("任务", array, warnings)
        return results
    }

    private fun validatePlans(
        array: JSONArray?,
        snapshot: AiContextSnapshot,
        warnings: MutableList<String>
    ): List<AiFilePlanCandidate> {
        if (array == null) return emptyList()
        val validDates = snapshot.days.mapTo(linkedSetOf()) { it.date }
        val occupiedByDate = snapshot.days.associate { day ->
            day.date to day.occupiedTimeRanges.mapNotNull { range ->
                val start = parseMinutes(range.startTime) ?: return@mapNotNull null
                val end = parseMinutes(range.endTime) ?: return@mapNotNull null
                start to end
            }
        }
        val results = mutableListOf<AiFilePlanCandidate>()
        val keys = mutableSetOf<String>()
        for (index in 0 until minOf(array.length(), MAX_SCANNED_ITEMS)) {
            val item = array.optJSONObject(index) ?: continue
            val evidenceRefs = validatedEvidenceRefs(item, snapshot.allowedLocalRefs)
            if (AI_FILE_SELECTION_REF !in evidenceRefs) {
                warnings += "第 ${index + 1} 条计划缺少文件依据，已忽略"
                continue
            }
            if (hasFractionalNumber(item, "plannedMinutes")) {
                warnings += "第 ${index + 1} 条计划时长不是整数，已忽略"
                continue
            }
            val title = normalizedText(item.optString("title"), MAX_NAME_CHARS)
            val planDate = item.optString("planDate").trim()
            val plannedMinutes = item.optInt("plannedMinutes", 0)
            val start = parseMinutes(item.optString("startTime"))
            val end = parseMinutes(item.optString("endTime"))
            val invalidReason = when {
                title.isBlank() -> "缺少标题"
                planDate !in validDates -> "日期不在允许范围"
                plannedMinutes !in MIN_PLAN_MINUTES..MAX_PLAN_MINUTES -> "时长不在 5-240 分钟"
                start == null || end == null || end <= start -> "开始或结束时间无效"
                occupiedByDate[planDate].orEmpty().any { (busyStart, busyEnd) ->
                    start < busyEnd && end > busyStart
                } -> "与已有安排冲突"
                else -> null
            }
            if (invalidReason != null) {
                warnings += "计划“${title.ifBlank { (index + 1).toString() }}”$invalidReason，已忽略"
                continue
            }
            val draft = AiFilePlanDraft(
                title = title,
                planDate = planDate,
                plannedMinutes = plannedMinutes,
                startTime = formatMinutes(requireNotNull(start)),
                endTime = formatMinutes(requireNotNull(end))
            )
            val key = listOf(draft.title, draft.planDate, draft.startTime, draft.endTime)
                .joinToString("|").lowercase()
            if (keys.add(key)) results += AiFilePlanCandidate(draft, evidenceRefs)
            if (results.size == MAX_CATEGORY_ITEMS) break
        }
        appendOverflowWarning("计划", array, warnings)
        return results
    }

    private fun validateInsights(
        array: JSONArray?,
        allowedLocalRefs: Set<String>,
        warnings: MutableList<String>
    ): List<AiFileInsight> {
        if (array == null) return emptyList()
        val results = mutableListOf<AiFileInsight>()
        val keys = mutableSetOf<String>()
        for (index in 0 until minOf(array.length(), MAX_SCANNED_ITEMS)) {
            val item = array.optJSONObject(index) ?: continue
            val evidenceRefs = validatedEvidenceRefs(item, allowedLocalRefs)
            val title = normalizedText(item.optString("title"), MAX_TITLE_CHARS)
            val detail = normalizedText(item.optString("detail"), MAX_DETAIL_CHARS)
            if (AI_FILE_SELECTION_REF !in evidenceRefs || title.isBlank() || detail.isBlank()) {
                warnings += "第 ${index + 1} 条学习要点缺少文件依据或正文，已忽略"
                continue
            }
            val localRefs = readStrings(item.optJSONArray("localRefs"), MAX_LOCAL_REFS)
                .filter(allowedLocalRefs::contains)
            val key = "$title\u0000$detail".lowercase()
            if (keys.add(key)) results += AiFileInsight(title, detail, evidenceRefs, localRefs)
            if (results.size == MAX_INSIGHTS) break
        }
        return results
    }

    private fun validateLocalMatches(
        array: JSONArray?,
        allowedLocalRefs: Set<String>,
        warnings: MutableList<String>
    ): List<AiFileLocalMatch> {
        if (array == null) return emptyList()
        val results = mutableListOf<AiFileLocalMatch>()
        val seen = mutableSetOf<String>()
        for (index in 0 until minOf(array.length(), MAX_SCANNED_ITEMS)) {
            val item = array.optJSONObject(index) ?: continue
            val localRef = item.optString("localRef").trim()
            val reason = normalizedText(item.optString("reason"), MAX_DETAIL_CHARS)
            val evidenceRefs = validatedEvidenceRefs(item, allowedLocalRefs)
            if (localRef !in allowedLocalRefs || reason.isBlank() || AI_FILE_SELECTION_REF !in evidenceRefs) {
                warnings += "第 ${index + 1} 条本地关联缺少允许的引用或文件依据，已忽略"
                continue
            }
            if (seen.add(localRef)) results += AiFileLocalMatch(localRef, reason, evidenceRefs)
            if (results.size == MAX_LOCAL_MATCHES) break
        }
        return results
    }

    private fun validatedEvidenceRefs(
        item: JSONObject,
        allowedLocalRefs: Set<String>
    ): List<String> {
        return readStrings(item.optJSONArray("evidenceRefs"), MAX_EVIDENCE_REFS)
            .filter { it == AI_FILE_SELECTION_REF || it in allowedLocalRefs }
    }

    private fun copyFields(source: JSONObject, vararg names: String): JSONObject {
        return JSONObject().apply {
            names.forEach { name ->
                if (source.has(name) && source.opt(name) != JSONObject.NULL) put(name, source.opt(name))
            }
        }
    }

    private fun removeInvalidDateTime(
        item: JSONObject,
        field: String,
        index: Int,
        warnings: MutableList<String>
    ) {
        if (!item.has(field)) return
        val value = item.opt(field)
        if (value is String && !DATE_TIME_PATTERN.matches(value.trim())) {
            item.remove(field)
            warnings += "第 ${index + 1} 条任务的 $field 格式无效，已留空"
        }
    }

    private fun hasFractionalNumber(item: JSONObject, vararg fields: String): Boolean {
        return fields.any { field ->
            val value = item.opt(field)
            value is Number && value.toDouble() % 1.0 != 0.0
        }
    }

    private fun readStrings(array: JSONArray?, limit: Int): List<String> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), MAX_SCANNED_STRINGS))
            .mapNotNull { index -> array.optString(index).trim().takeIf(String::isNotBlank) }
            .distinct()
            .take(limit)
    }

    private fun appendOverflowWarning(
        label: String,
        array: JSONArray,
        warnings: MutableList<String>
    ) {
        if (array.length() > MAX_CATEGORY_ITEMS) {
            warnings += "模型返回的${label}过多，仅保留前 $MAX_CATEGORY_ITEMS 条有效结果"
        }
    }

    private fun normalizedText(value: String, maxChars: Int): String {
        return value.trim().replace(WHITESPACE, " ").take(maxChars)
    }

    private fun parseMinutes(value: String?): Int? {
        val match = TIME_PATTERN.matchEntire(value?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }

    private fun formatMinutes(minutes: Int): String {
        return "%02d:%02d".format(minutes / 60, minutes % 60)
    }

    companion object {
        private val WHITESPACE = Regex("""\s+""")
        private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")
        private val DATE_TIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""")
        private const val SCHEMA_VERSION = 1
        private const val MAX_SUMMARY_CHARS = 600
        private const val MAX_KEY_POINTS = 6
        private const val MAX_KEY_POINT_CHARS = 180
        private const val MAX_NAME_CHARS = 120
        private const val MAX_TITLE_CHARS = 80
        private const val MAX_DETAIL_CHARS = 360
        private const val MAX_WARNING_CHARS = 200
        private const val MAX_MODEL_WARNINGS = 8
        private const val MAX_COMBINED_WARNINGS = 16
        private const val MAX_SCANNED_ITEMS = 40
        private const val MAX_CATEGORY_ITEMS = 20
        private const val MAX_INSIGHTS = 12
        private const val MAX_LOCAL_MATCHES = 12
        private const val MAX_EVIDENCE_REFS = 6
        private const val MAX_LOCAL_REFS = 6
        private const val MAX_SCANNED_STRINGS = 40
        private const val MIN_PLAN_MINUTES = 5
        private const val MAX_PLAN_MINUTES = 240
    }
}
