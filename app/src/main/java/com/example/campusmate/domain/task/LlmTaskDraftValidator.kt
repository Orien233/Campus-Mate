package com.example.campusmate.domain.task

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.llm.LlmJsonPayloadExtractor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class LlmTaskDraftValidationResult(
    val drafts: List<TaskDraft>,
    val warnings: List<String>
)

class LlmTaskDraftValidator(
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun parse(rawText: String): LlmTaskDraftValidationResult {
        val jsonText = extractJsonText(rawText)
        val root = parseRoot(jsonText)

        val warnings = mutableListOf<String>()
        val drafts = mutableListOf<TaskDraft>()

        warnings += readWarnings(root)
        readTaskObjects(root).forEachIndexed { index, taskObject ->
            val parsed = parseTask(taskObject, index + 1)
            warnings += parsed.warnings
            parsed.draft?.let(drafts::add)
        }

        if (drafts.isEmpty()) {
            throw TaskParseException("AI 返回中未识别到任何合法任务。")
        }

        return LlmTaskDraftValidationResult(
            drafts = drafts.distinctBy { draftKey(it) },
            warnings = warnings.distinct()
        )
    }

    private fun parseRoot(jsonText: String): Any {
        val trimmed = jsonText.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> throw TaskParseException("AI 返回内容不包含 JSON。")
            }
        } catch (error: JSONException) {
            throw TaskParseException("AI 返回 JSON 解析失败：${error.message ?: "invalid JSON"}")
        }
    }

    private fun readTaskObjects(root: Any): List<JSONObject> {
        return when (root) {
            is JSONObject -> {
                val array = readJsonArray(root, "tasks", "taskList", "items", "drafts")
                when {
                    array != null -> array.toJSONObjectList()
                    root.has("title") -> listOf(root)
                    else -> throw TaskParseException("AI 返回缺少 tasks 数组。")
                }
            }
            is JSONArray -> root.toJSONObjectList()
            else -> emptyList()
        }
    }

    private fun readWarnings(root: Any): List<String> {
        return when (root) {
            is JSONObject -> {
                val warningsArray = readJsonArray(root, "warnings", "warningList")
                if (warningsArray != null) {
                    warningsArray.toStringList()
                } else {
                    readStringOrNull(root, "warnings", "warning", "message")
                        ?.split('\n', ';')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseTask(taskObject: JSONObject, index: Int): ParsedTask {
        val warnings = mutableListOf<String>()
        val title = readStringOrNull(taskObject, "title", "name", "taskTitle", "task_name")
            ?.trim()
            .orEmpty()
        if (title.isBlank()) {
            warnings += "第 $index 条任务缺少标题。"
        }

        val type = parseTaskType(readJsonValue(taskObject, "type", "taskType", "category"))
        if (type == null) {
            warnings += "第 $index 条任务类型不合法，已跳过。"
        }

        val priority = parsePriority(readJsonValue(taskObject, "priority", "importance", "urgency"))
            ?: StudyTask.PRIORITY_NORMAL

        if (warnings.isNotEmpty()) {
            return ParsedTask(null, warnings)
        }

        val rawDueAt = readJsonValue(
            taskObject,
            "dueAt", "dueTime", "due_time", "deadline", "deadlineAt", "dueDate", "due_date", "endTime"
        )
        val rawRemindAt = readJsonValue(
            taskObject,
            "remindAt", "reminderAt", "reminderTime", "reminder_time", "notifyAt", "notify_at"
        )
        val dueAt = parseTime(rawDueAt)
        val remindAt = parseTime(rawRemindAt)
        if (rawDueAt != null && dueAt == null) warnings += "第 $index 条任务截止时间格式无法识别，已留空。"
        if (rawRemindAt != null && remindAt == null) warnings += "第 $index 条任务提醒时间格式无法识别，已留空。"

        return ParsedTask(
            draft = TaskDraft(
                title = title,
                description = readStringOrNull(taskObject, "description", "desc", "detail", "content", "note"),
                courseName = readStringOrNull(taskObject, "courseName", "course", "subject", "className"),
                type = type ?: StudyTask.TYPE_OTHER,
                priority = priority,
                dueAt = dueAt,
                remindAt = remindAt,
                sourceText = readStringOrNull(taskObject, "sourceText", "source", "rawText"),
                warnings = warnings
            ),
            warnings = warnings
        )
    }

    private fun parseTaskType(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt().takeIf { it in 0..5 }
            is String -> {
                val value = raw.trim().lowercase(Locale.ROOT)
                when {
                    value in setOf("0", "homework", "assignment", "作业", "习题") ||
                        value.contains("作业") || value.contains("homework") || value.contains("assignment") -> StudyTask.TYPE_HOMEWORK
                    value in setOf("1", "experiment", "lab", "实验", "实验报告") ||
                        value.contains("实验") || value.contains("experiment") || value.contains("lab") -> StudyTask.TYPE_EXPERIMENT
                    value in setOf("2", "exam", "test", "quiz", "考试", "测验", "期中", "期末") ||
                        value.contains("考试") || value.contains("测验") || value.contains("exam") || value.contains("quiz") -> StudyTask.TYPE_EXAM
                    value in setOf("3", "review", "revision", "复习", "预习") ||
                        value.contains("复习") || value.contains("预习") || value.contains("review") -> StudyTask.TYPE_REVIEW
                    value in setOf("4", "project", "项目", "大作业", "课程设计") ||
                        value.contains("项目") || value.contains("课程设计") || value.contains("project") -> StudyTask.TYPE_PROJECT
                    value in setOf("5", "other", "其他") -> StudyTask.TYPE_OTHER
                    else -> null
                }
            }
            null -> StudyTask.TYPE_HOMEWORK
            else -> null
        }
    }

    private fun parsePriority(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt().takeIf { it in 0..2 }
            is String -> {
                val value = raw.trim().lowercase(Locale.ROOT)
                when {
                    value in setOf("0", "low", "低", "不急") || value.contains("可选") -> StudyTask.PRIORITY_LOW
                    value in setOf("2", "high", "urgent", "important", "高", "紧急", "重要", "快截止") ||
                        value.contains("紧急") || value.contains("重要") || value.contains("urgent") || value.contains("important") -> StudyTask.PRIORITY_HIGH
                    value in setOf("1", "normal", "medium", "middle", "普通", "正常", "中") -> StudyTask.PRIORITY_NORMAL
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun parseTime(raw: Any?): Long? {
        return when (raw) {
            is Number -> normalizeEpoch(raw.toLong())
            is String -> parseTimeString(raw)
            else -> null
        }
    }

    private fun normalizeEpoch(value: Long): Long? {
        if (value <= 0L) return null
        return if (value < 10_000_000_000L) value * 1000L else value
    }

    private fun parseTimeString(raw: String): Long? {
        val text = raw.trim()
        if (text.isBlank()) return null
        parseRelativeDate(text)?.let { return it }
        parseRelativeWeekday(text)?.let { return it }
        DATE_TIME_FORMATS.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
                    .parse(text)
                    ?.time
            }.getOrNull()?.let { return it }
        }
        parseMonthDayTime(text)?.let { return it }
        return null
    }

    private fun parseRelativeDate(text: String): Long? {
        val dayOffset = when {
            text.contains("后天") -> 2
            text.contains("明天") -> 1
            text.contains("今天") || text.contains("今日") -> 0
            else -> return null
        }
        val timeMatch = Regex("""(\d{1,2})[:：](\d{1,2})""").find(text)
        val hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 23
        val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 59
        return Calendar.getInstance().apply {
            timeInMillis = nowMillisProvider()
            add(Calendar.DAY_OF_MONTH, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parseRelativeWeekday(text: String): Long? {
        val match = Regex("""(本周|这周|下周)?\s*(?:星期|周)([一二三四五六日天])""").find(text) ?: return null
        val target = "一二三四五六日".indexOf(match.groupValues[2].replace('天', '日')) + 1
        if (target !in 1..7) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillisProvider() }
        val current = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        var offset = target - current
        if (match.groupValues[1] == "下周") offset += 7
        else if (match.groupValues[1].isBlank() && offset < 0) offset += 7
        val timeMatch = Regex("""(\d{1,2})[:：](\d{1,2})""").find(text)
        val hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 23
        val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 59
        calendar.add(Calendar.DAY_OF_MONTH, offset)
        calendar.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        calendar.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun parseMonthDayTime(text: String): Long? {
        val match = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日?(?:\s+(\d{1,2})[:：](\d{1,2}))?""")
            .find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val hour = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 23
        val minute = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 59
        return Calendar.getInstance().apply {
            timeInMillis = nowMillisProvider()
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis < nowMillisProvider() - ONE_DAY_MILLIS) {
                add(Calendar.YEAR, 1)
            }
        }.timeInMillis
    }

    private fun extractJsonText(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            throw TaskParseException("AI 返回内容为空。")
        }
        return LlmJsonPayloadExtractor.extract(trimmed)
            ?: throw TaskParseException("AI 返回内容不包含可解析的 JSON。")
    }

    private fun readJsonArray(root: JSONObject, vararg names: String): JSONArray? {
        return names.firstNotNullOfOrNull { name ->
            when (val value = root.opt(name)) {
                is JSONArray -> value
                is Collection<*> -> JSONArray(value)
                else -> null
            }
        }
    }

    private fun readJsonValue(root: JSONObject, vararg names: String): Any? {
        return names.firstNotNullOfOrNull { name ->
            root.opt(name)?.takeIf { it != JSONObject.NULL }
        }
    }

    private fun readStringOrNull(root: JSONObject, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            root.opt(name)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun JSONArray.toJSONObjectList(): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        for (index in 0 until length()) {
            val item = opt(index)
            if (item is JSONObject) {
                items += item
            }
        }
        return items
    }

    private fun JSONArray.toStringList(): List<String> {
        val items = mutableListOf<String>()
        for (index in 0 until length()) {
            val item = opt(index)?.toString()?.trim().orEmpty()
            if (item.isNotBlank()) {
                items += item
            }
        }
        return items
    }

    private fun draftKey(draft: TaskDraft): String {
        return listOf(
            draft.title,
            draft.courseName.orEmpty(),
            draft.type,
            draft.priority,
            draft.dueAt ?: 0L,
            draft.remindAt ?: 0L
        ).joinToString("|")
    }

    companion object {
        private val DATE_TIME_FORMATS = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy.MM.dd HH:mm",
            "yyyy年M月d日 HH:mm",
            "yyyy年M月d日HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "yyyy年M月d日"
        )
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
    }

    private data class ParsedTask(
        val draft: TaskDraft?,
        val warnings: List<String>
    )
}

class TaskParseException(message: String) : IllegalArgumentException(message)
