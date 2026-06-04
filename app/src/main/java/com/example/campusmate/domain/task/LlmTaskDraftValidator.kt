package com.example.campusmate.domain.task

import com.example.campusmate.data.model.StudyTask
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

        val dueAt = parseTime(readJsonValue(taskObject, "dueAt", "dueTime", "deadline", "dueDate", "endTime"))
        val remindAt = parseTime(readJsonValue(taskObject, "remindAt", "reminderAt", "reminderTime", "notifyAt"))

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
            is String -> when (raw.trim().lowercase(Locale.ROOT)) {
                "0", "homework", "assignment", "作业", "习题" -> StudyTask.TYPE_HOMEWORK
                "1", "experiment", "lab", "实验", "实验报告" -> StudyTask.TYPE_EXPERIMENT
                "2", "exam", "test", "quiz", "考试", "测验", "期中", "期末" -> StudyTask.TYPE_EXAM
                "3", "review", "revision", "复习", "预习" -> StudyTask.TYPE_REVIEW
                "4", "project", "项目", "大作业", "课程设计" -> StudyTask.TYPE_PROJECT
                "5", "other", "其他" -> StudyTask.TYPE_OTHER
                else -> null
            }
            null -> StudyTask.TYPE_HOMEWORK
            else -> null
        }
    }

    private fun parsePriority(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt().takeIf { it in 0..2 }
            is String -> when (raw.trim().lowercase(Locale.ROOT)) {
                "0", "low", "低", "不急" -> StudyTask.PRIORITY_LOW
                "1", "normal", "medium", "middle", "普通", "正常", "中" -> StudyTask.PRIORITY_NORMAL
                "2", "high", "urgent", "important", "高", "紧急", "重要", "快截止" -> StudyTask.PRIORITY_HIGH
                else -> null
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
        }.timeInMillis
    }

    private fun extractJsonText(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            throw TaskParseException("AI 返回内容为空。")
        }
        fencedJson(trimmed)?.let { return it }

        val objectStart = trimmed.indexOfFirst { it == '{' || it == '[' }
        val objectEnd = trimmed.indexOfLast { it == '}' || it == ']' }
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1)
        }

        throw TaskParseException("AI 返回内容不包含可解析的 JSON。")
    }

    private fun fencedJson(text: String): String? {
        val fenceStart = text.indexOf("```")
        if (fenceStart < 0) return null
        val firstLineBreak = text.indexOf('\n', fenceStart)
        if (firstLineBreak < 0) return null
        val fenceEnd = text.lastIndexOf("```")
        if (fenceEnd <= firstLineBreak) return null
        return text.substring(firstLineBreak + 1, fenceEnd).trim()
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
            "yyyy年M月d日 HH:mm",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy年M月d日"
        )
    }

    private data class ParsedTask(
        val draft: TaskDraft?,
        val warnings: List<String>
    )
}

class TaskParseException(message: String) : IllegalArgumentException(message)
