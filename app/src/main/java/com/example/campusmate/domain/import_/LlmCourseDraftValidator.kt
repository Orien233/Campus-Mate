package com.example.campusmate.domain.import_

import com.example.campusmate.data.model.Course
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class LlmCourseDraftValidationResult(
    val drafts: List<CourseDraft>,
    val warnings: List<String>
)

class LlmCourseDraftValidator {
    fun parse(rawText: String): LlmCourseDraftValidationResult {
        val jsonText = extractJsonText(rawText)
        val root = parseRoot(jsonText)

        val warnings = mutableListOf<String>()
        val drafts = mutableListOf<CourseDraft>()

        warnings += readWarnings(root)

        val courseObjects = readCourseObjects(root)
        courseObjects.forEachIndexed { index, courseObject ->
            val parsed = parseCourse(courseObject, index + 1)
            warnings += parsed.warnings
            parsed.draft?.let(drafts::add)
        }

        if (drafts.isEmpty()) {
            throw ScheduleParseException("AI 返回中未识别到任何合法课程。")
        }

        return LlmCourseDraftValidationResult(
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
                else -> throw ScheduleParseException("AI 返回内容不包含 JSON。")
            }
        } catch (error: JSONException) {
            throw ScheduleParseException("AI 返回 JSON 解析失败：${error.message ?: "invalid JSON"}")
        }
    }

    private fun readCourseObjects(root: Any): List<JSONObject> {
        return when (root) {
            is JSONObject -> {
                val array = readJsonArray(root, "courses", "courseList", "items", "drafts")
                    ?: throw ScheduleParseException("AI 返回缺少 courses 数组。")
                array.toJSONObjectList()
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
                    readJsonString(root, "warnings", "warning", "message")
                        ?.split('\n', ';')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }
            }
            is JSONArray -> emptyList()
            else -> emptyList()
        }
    }

    private fun parseCourse(courseObject: JSONObject, index: Int): ParsedCourse {
        val warnings = mutableListOf<String>()
        val name = readString(courseObject, "name", "courseName", "course_name", "title").trim()
        if (name.isBlank()) {
            warnings += "第 $index 条课程缺少课程名。"
        }

        val weekday = readInt(courseObject, "weekday", "weekDay", "dayOfWeek", "day_of_week")
        if (weekday == null || weekday !in 1..7) {
            warnings += "第 $index 条课程星期字段不合法（应为 1..7）。"
        }

        val startSection = readInt(courseObject, "startSection", "start_section", "sectionStart", "section_start")
        val endSection = readInt(courseObject, "endSection", "end_section", "sectionEnd", "section_end")
        if (startSection == null || endSection == null || startSection <= 0 || endSection <= 0 || startSection > endSection || endSection > MAX_SECTION) {
            warnings += "第 $index 条课程节次范围不合法（startSection/endSection）。"
        }

        val startWeek = readInt(courseObject, "startWeek", "start_week", "weekStart", "week_start")
        val endWeek = readInt(courseObject, "endWeek", "end_week", "weekEnd", "week_end")
        if (startWeek == null || endWeek == null || startWeek <= 0 || endWeek <= 0 || startWeek > endWeek || endWeek > MAX_WEEK) {
            warnings += "第 $index 条课程周次范围不合法（startWeek/endWeek）。"
        }

        val weekType = parseWeekType(courseObject)
        if (weekType == null) {
            warnings += "第 $index 条课程单双周字段不合法（weekType 应为 0/1/2）。"
        }

        if (warnings.isNotEmpty()) {
            return ParsedCourse(draft = null, warnings = warnings)
        }

        return ParsedCourse(
            draft = CourseDraft(
                name = name,
                teacher = readStringOrNull(courseObject, "teacher", "teacherName", "teacher_name"),
                classroom = readStringOrNull(courseObject, "classroom", "room", "classRoom", "class_room"),
                weekday = weekday!!,
                startSection = startSection!!,
                endSection = endSection!!,
                startWeek = startWeek!!,
                endWeek = endWeek!!,
                weekType = weekType!!,
                color = readStringOrNull(courseObject, "color", "courseColor"),
                note = readStringOrNull(courseObject, "note", "remark", "comments"),
                sourceText = courseObject.toString()
            ),
            warnings = warnings
        )
    }

    private fun parseWeekType(courseObject: JSONObject): Int? {
        val raw = readJsonValue(courseObject, "weekType", "week_type", "type")
            ?: return Course.WEEK_TYPE_EVERY
        return when (raw) {
            is Number -> when (raw.toInt()) {
                Course.WEEK_TYPE_EVERY, Course.WEEK_TYPE_ODD, Course.WEEK_TYPE_EVEN -> raw.toInt()
                else -> null
            }
            is String -> when (raw.trim().lowercase()) {
                "", "0", "every", "all", "full", "both" -> Course.WEEK_TYPE_EVERY
                "1", "odd", "odd_weeks", "oddweek" -> Course.WEEK_TYPE_ODD
                "2", "even", "even_weeks", "evenweek" -> Course.WEEK_TYPE_EVEN
                else -> null
            }
            else -> null
        }
    }

    private fun draftKey(draft: CourseDraft): String {
        return listOf(
            draft.name,
            draft.teacher.orEmpty(),
            draft.classroom.orEmpty(),
            draft.weekday,
            draft.startSection,
            draft.endSection,
            draft.startWeek,
            draft.endWeek,
            draft.weekType,
            draft.color.orEmpty(),
            draft.note.orEmpty()
        ).joinToString("|")
    }

    private fun extractJsonText(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            throw ScheduleParseException("AI 返回内容为空。")
        }

        fencedJson(trimmed)?.let { return it }

        val objectStart = trimmed.indexOfFirst { it == '{' || it == '[' }
        val objectEnd = trimmed.indexOfLast { it == '}' || it == ']' }
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1)
        }

        throw ScheduleParseException("AI 返回内容不包含可解析的 JSON。")
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

    private fun readJsonString(root: JSONObject, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            root.opt(name)?.takeIf { it is String }?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun readJsonValue(root: JSONObject, vararg names: String): Any? {
        return names.firstNotNullOfOrNull { name ->
            root.opt(name)?.takeIf { it != JSONObject.NULL }
        }
    }

    private fun readString(root: JSONObject, vararg names: String): String {
        return readStringOrNull(root, *names).orEmpty()
    }

    private fun readStringOrNull(root: JSONObject, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            root.opt(name)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun readInt(root: JSONObject, vararg names: String): Int? {
        return names.firstNotNullOfOrNull { name ->
            when (val value = root.opt(name)) {
                is Number -> value.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
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

    companion object {
        private const val MAX_SECTION = 40
        private const val MAX_WEEK = 60
    }

    private data class ParsedCourse(
        val draft: CourseDraft?,
        val warnings: List<String>
    )
}
