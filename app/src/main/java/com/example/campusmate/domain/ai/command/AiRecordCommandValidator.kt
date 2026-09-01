package com.example.campusmate.domain.ai.command

import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.llm.LlmJsonPayloadExtractor
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class AiRecordCommandValidator {
    data class ValidationResult(
        val changes: List<AiRecordChange>,
        val warnings: List<String>
    )

    fun parseAndValidate(
        rawText: String,
        originalInput: String,
        context: AiRecordCommandContext
    ): ValidationResult {
        val payload = LlmJsonPayloadExtractor.extractObject(rawText)
            ?: return ValidationResult(emptyList(), listOf("未找到有效 JSON 对象"))
        return runCatching { validateRoot(JSONObject(payload), originalInput, context) }
            .getOrElse { ValidationResult(emptyList(), listOf("记录指令 JSON 解析失败")) }
    }

    private fun validateRoot(
        root: JSONObject,
        originalInput: String,
        context: AiRecordCommandContext
    ): ValidationResult {
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            return ValidationResult(emptyList(), listOf("记录指令 schemaVersion 不受支持"))
        }
        val warnings = readStrings(root.optJSONArray("warnings"), 10).toMutableList()
        val array = root.optJSONArray("changes") ?: JSONArray()
        val changes = mutableListOf<AiRecordChange>()
        val seenTargets = mutableSetOf<String>()
        val scanCount = minOf(array.length(), MAX_CHANGES)
        for (index in 0 until scanCount) {
            val item = array.optJSONObject(index)
            if (item == null) {
                warnings += "第 ${index + 1} 项不是对象，已忽略"
                continue
            }
            val attempt = runCatching { validateItem(item, index, originalInput, context) }
            val change = attempt.getOrNull()
            if (change == null) {
                warnings += "第 ${index + 1} 项${attempt.exceptionOrNull()?.message?.let { "：$it" }.orEmpty()}，已忽略"
                continue
            }
            val target = change.targetRef
            if (target != null && !seenTargets.add(target)) {
                warnings += "目标 $target 重复，仅保留第一项"
                continue
            }
            changes += change
        }
        if (array.length() > MAX_CHANGES) warnings += "变更超过 30 项，仅检查前 30 项"
        return ValidationResult(changes, warnings.distinct().take(MAX_WARNINGS))
    }

    private fun validateItem(
        item: JSONObject,
        index: Int,
        originalInput: String,
        context: AiRecordCommandContext
    ): AiRecordChange {
        val type = when (item.optString("recordType").lowercase()) {
            "course" -> AiRecordType.COURSE
            "task" -> AiRecordType.TASK
            "plan" -> AiRecordType.PLAN
            else -> invalid("记录类型无效")
        }
        val operation = when (item.optString("operation").lowercase()) {
            "create" -> AiRecordOperation.CREATE
            "update" -> AiRecordOperation.UPDATE
            "delete" -> AiRecordOperation.DELETE
            else -> invalid("操作类型无效")
        }
        val evidence = item.optString("evidenceQuote").trim().take(MAX_EVIDENCE_CHARS)
        if (evidence.isBlank() || !originalInput.contains(evidence, ignoreCase = true)) {
            invalid("证据片段不在输入原文中")
        }
        val targetRef = item.optString("targetRef").trim().takeIf(String::isNotBlank)
        if (operation != AiRecordOperation.CREATE) {
            if (targetRef == null || targetRef !in context.allowedRefs) invalid("目标引用不在允许范围")
            if (!targetRef.startsWith(type.name.lowercase() + ":")) invalid("目标引用类型不匹配")
        } else if (targetRef != null) {
            invalid("新增不应包含目标引用")
        }
        if (operation == AiRecordOperation.DELETE && !DELETE_INTENT.containsMatchIn(evidence)) {
            invalid("删除项缺少明确删除意图")
        }
        val fields = item.optJSONObject("fields") ?: JSONObject()
        if (operation == AiRecordOperation.UPDATE && fields.length() == 0) invalid("修改项没有明确字段")
        val before = targetRef?.let { findSnapshot(it, context) }
        val after = when {
            operation == AiRecordOperation.DELETE -> null
            type == AiRecordType.COURSE -> validateCourse(fields, before as? AiCourseSnapshot)
            type == AiRecordType.TASK -> validateTask(fields, before as? AiTaskSnapshot, context)
            else -> validatePlan(fields, before as? AiPlanSnapshot)
        }
        val conflict = when (after) {
            is AiCourseSnapshot -> hasCourseConflict(after, context)
            is AiPlanSnapshot -> hasPlanConflict(after, context)
            else -> false
        }
        return AiRecordChange(
            changeId = "change-${index + 1}",
            recordType = type,
            operation = operation,
            targetRef = targetRef,
            expectedUpdatedAt = before?.updatedAt,
            evidenceQuote = evidence,
            before = before,
            after = after,
            hasTimeConflict = conflict,
            warnings = if (conflict) listOf("与现有时间安排冲突，默认不选中") else emptyList()
        )
    }

    private fun validateCourse(fields: JSONObject, before: AiCourseSnapshot?): AiCourseSnapshot {
        rejectUnknown(fields, COURSE_FIELDS)
        val name = stringValue(fields, "name", before?.name, false, 120)
            ?: invalid("课程名称不能为空")
        val teacher = stringValue(fields, "teacher", before?.teacher, true, 80)
        val classroom = stringValue(fields, "classroom", before?.classroom, true, 80)
        val weekday = intValue(fields, "weekday", before?.weekday ?: 0)
        val startSection = intValue(fields, "startSection", before?.startSection ?: 0)
        val endSection = intValue(fields, "endSection", before?.endSection ?: 0)
        val startWeek = intValue(fields, "startWeek", before?.startWeek ?: 1)
        val endWeek = intValue(fields, "endWeek", before?.endWeek ?: 18)
        val weekType = intValue(fields, "weekType", before?.weekType ?: Course.WEEK_TYPE_EVERY)
        val color = stringValue(fields, "color", before?.color, true, 16)
        val note = stringValue(fields, "note", before?.note, true, 500)
        if (weekday !in 1..7 || startSection !in 1..20 || endSection !in startSection..20) invalid("课程星期或节次无效")
        if (startWeek !in 1..60 || endWeek !in startWeek..60 || weekType !in 0..2) invalid("课程周次无效")
        if (color != null && !COLOR.matches(color)) invalid("课程颜色格式无效")
        return AiCourseSnapshot(
            before?.id ?: 0L, name, teacher, classroom, weekday, startSection, endSection,
            startWeek, endWeek, weekType, color, note, before?.updatedAt ?: 0L
        )
    }

    private fun validateTask(
        fields: JSONObject,
        before: AiTaskSnapshot?,
        context: AiRecordCommandContext
    ): AiTaskSnapshot {
        rejectUnknown(fields, TASK_FIELDS)
        val title = stringValue(fields, "title", before?.title, false, 120)
            ?: invalid("任务标题不能为空")
        val description = stringValue(fields, "description", before?.description, true, 1000)
        val courseId = if (!fields.has("courseRef")) {
            before?.courseId
        } else if (fields.isNull("courseRef")) {
            null
        } else {
            val ref = fields.optString("courseRef").trim()
            if (ref !in context.allowedRefs || !ref.startsWith("course:")) invalid("任务课程引用无效")
            ref.substringAfter(':').toLongOrNull() ?: invalid("任务课程引用无效")
        }
        val type = intValue(fields, "type", before?.type ?: StudyTask.TYPE_HOMEWORK)
        val priority = intValue(fields, "priority", before?.priority ?: StudyTask.PRIORITY_NORMAL)
        val dueAt = dateTimeValue(fields, "dueAt", before?.dueAt)
        val remindAt = dateTimeValue(fields, "remindAt", before?.remindAt)
        val status = intValue(fields, "status", before?.status ?: StudyTask.STATUS_TODO)
        if (type !in 0..5 || priority !in 0..2 || status !in 0..2) invalid("任务枚举字段无效")
        if (dueAt != null && remindAt != null && remindAt > dueAt) invalid("任务提醒晚于截止时间")
        return AiTaskSnapshot(
            before?.id ?: 0L, courseId, title, description, type, priority,
            dueAt, remindAt, status, before?.updatedAt ?: 0L
        )
    }

    private fun validatePlan(fields: JSONObject, before: AiPlanSnapshot?): AiPlanSnapshot {
        rejectUnknown(fields, PLAN_FIELDS)
        val title = stringValue(fields, "title", before?.title, false, 120)
            ?: invalid("计划标题不能为空")
        val planDate = stringValue(fields, "planDate", before?.planDate, false, 10)
            ?: invalid("计划日期不能为空")
        if (parseStrict(planDate, "yyyy-MM-dd") == null) invalid("计划日期格式无效")
        val plannedMinutes = intValue(fields, "plannedMinutes", before?.plannedMinutes ?: 0)
        if (plannedMinutes !in 5..1440) invalid("计划时长无效")
        val startTime = timeValue(fields, "startTime", before?.startTime)
        val endTime = timeValue(fields, "endTime", before?.endTime)
        if ((startTime == null) != (endTime == null)) invalid("计划开始和结束时间必须同时填写或清空")
        if (startTime != null && endTime != null && minutes(endTime) <= minutes(startTime)) invalid("计划结束时间必须晚于开始时间")
        val type = intValue(fields, "type", before?.type ?: StudyPlan.TYPE_DAILY)
        val status = intValue(fields, "status", before?.status ?: StudyPlan.STATUS_PENDING)
        if (type !in 0..1 || status !in 0..2) invalid("计划枚举字段无效")
        return AiPlanSnapshot(
            before?.id ?: 0L, title, planDate, plannedMinutes,
            startTime, endTime, type, status, before?.updatedAt ?: 0L
        )
    }

    private fun findSnapshot(ref: String, context: AiRecordCommandContext): AiRecordSnapshot =
        when {
            ref.startsWith("course:") -> context.courses.firstOrNull { it.id == ref.idPart() }
            ref.startsWith("task:") -> context.tasks.firstOrNull { it.id == ref.idPart() }
            ref.startsWith("plan:") -> context.plans.firstOrNull { it.id == ref.idPart() }
            else -> null
        } ?: invalid("目标记录不存在")

    private fun hasCourseConflict(value: AiCourseSnapshot, context: AiRecordCommandContext): Boolean =
        context.courses.any {
            it.id != value.id && it.weekday == value.weekday &&
                value.startSection <= it.endSection && value.endSection >= it.startSection &&
                value.startWeek <= it.endWeek && value.endWeek >= it.startWeek &&
                (value.weekType == 0 || it.weekType == 0 || value.weekType == it.weekType)
        }

    private fun hasPlanConflict(value: AiPlanSnapshot, context: AiRecordCommandContext): Boolean {
        val start = value.startTime?.let(::minutes) ?: return false
        val end = value.endTime?.let(::minutes) ?: return false
        return context.plans.any {
            it.id != value.id && it.planDate == value.planDate &&
                it.startTime != null && it.endTime != null &&
                start < minutes(it.endTime) && end > minutes(it.startTime)
        }
    }

    private fun stringValue(fields: JSONObject, key: String, old: String?, nullable: Boolean, max: Int): String? {
        if (!fields.has(key)) return old
        if (fields.isNull(key)) {
            if (!nullable) invalid("$key 不可清空")
            return null
        }
        val value = fields.opt(key)
        if (value !is String) invalid("$key 必须是文本")
        val normalized = value.trim().replace(WHITESPACE, " ").take(max)
        if (!nullable && normalized.isBlank()) invalid("$key 不可为空")
        return normalized.takeIf(String::isNotBlank)
    }

    private fun intValue(fields: JSONObject, key: String, old: Int): Int {
        if (!fields.has(key)) return old
        val value = fields.opt(key)
        if (value !is Number || value.toDouble() % 1.0 != 0.0) invalid("$key 必须是整数")
        return value.toInt()
    }

    private fun dateTimeValue(fields: JSONObject, key: String, old: Long?): Long? {
        if (!fields.has(key)) return old
        if (fields.isNull(key)) return null
        val value = fields.opt(key)
        if (value !is String) invalid("$key 必须是 yyyy-MM-dd HH:mm")
        return parseStrict(value.trim(), "yyyy-MM-dd HH:mm") ?: invalid("$key 格式无效")
    }

    private fun timeValue(fields: JSONObject, key: String, old: String?): String? {
        if (!fields.has(key)) return old
        if (fields.isNull(key)) return null
        val value = fields.opt(key)
        if (value !is String || !TIME.matches(value.trim())) invalid("$key 格式无效")
        return value.trim()
    }

    private fun rejectUnknown(fields: JSONObject, allowed: Set<String>) {
        val unknown = fields.keys().asSequence().firstOrNull { it !in allowed }
        if (unknown != null) invalid("字段 $unknown 不允许修改")
    }

    private fun parseStrict(value: String, pattern: String): Long? = runCatching {
        SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)?.time
    }.getOrNull()

    private fun minutes(value: String): Int =
        value.substringBefore(':').toInt() * 60 + value.substringAfter(':').toInt()

    private fun String.idPart(): Long = substringAfter(':').toLongOrNull() ?: -1L

    private fun readStrings(array: JSONArray?, limit: Int): List<String> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), limit))
            .mapNotNull { array.optString(it).trim().take(200).takeIf(String::isNotBlank) }
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MAX_CHANGES = 30
        private const val MAX_WARNINGS = 24
        private const val MAX_EVIDENCE_CHARS = 240
        private val WHITESPACE = Regex("""\s+""")
        private val COLOR = Regex("""^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
        private val TIME = Regex("""^(?:[01]\d|2[0-3]):[0-5]\d$""")
        private val DELETE_INTENT = Regex("""删除|移除|删掉|作废并删除|delete|remove""", RegexOption.IGNORE_CASE)
        private val COURSE_FIELDS = setOf(
            "name", "teacher", "classroom", "weekday", "startSection", "endSection",
            "startWeek", "endWeek", "weekType", "color", "note"
        )
        private val TASK_FIELDS = setOf(
            "courseRef", "title", "description", "type", "priority",
            "dueAt", "remindAt", "status"
        )
        private val PLAN_FIELDS = setOf(
            "title", "planDate", "plannedMinutes", "startTime", "endTime", "type", "status"
        )
    }
}
