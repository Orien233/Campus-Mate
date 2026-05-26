package com.example.campusmate.domain.import_

import com.example.campusmate.data.model.Course
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Jsoup-based parser for sample HTML and common table-shaped schedules. */
class JsoupScheduleParser : ScheduleParser {
    override fun parse(html: String): List<CourseDraft> {
        if (html.isBlank()) {
            throw ScheduleParseException("HTML 内容为空，无法导入课表。")
        }

        val document = Jsoup.parse(html)
        val table = document.selectFirst("table[data-campusmate-schedule], table.schedule, table#scheduleTable, table")
            ?: throw ScheduleParseException("未找到课表 table，请粘贴包含课程表格的 HTML。")

        val drafts = parseDataAttributeCells(table).ifEmpty { parseMatrixTable(table) }
            .distinctBy { "${it.name}-${it.weekday}-${it.startSection}-${it.endSection}-${it.startWeek}-${it.endWeek}-${it.weekType}" }

        if (drafts.isEmpty()) {
            throw ScheduleParseException("未识别到课程。请确认 HTML 中包含课程名、星期和节次信息。")
        }
        return drafts
    }

    private fun parseDataAttributeCells(table: Element): List<CourseDraft> {
        return table.select("[data-course-name], [data-name]").mapNotNull { cell ->
            val name = cell.attrAny("data-course-name", "data-name").ifBlank { cell.ownText() }.trim()
            val weekday = cell.attrAny("data-weekday").toIntOrNull()
            val startSection = cell.attrAny("data-start-section", "data-section-start").toIntOrNull()
            val endSection = cell.attrAny("data-end-section", "data-section-end").toIntOrNull() ?: startSection
            if (name.isBlank() || weekday == null || startSection == null || endSection == null) {
                return@mapNotNull null
            }

            CourseDraft(
                name = name,
                teacher = cell.attrAny("data-teacher").blankToNull(),
                classroom = cell.attrAny("data-classroom", "data-room").blankToNull(),
                weekday = weekday,
                startSection = startSection,
                endSection = endSection,
                startWeek = cell.attrAny("data-start-week").toIntOrNull() ?: 1,
                endWeek = cell.attrAny("data-end-week").toIntOrNull() ?: 18,
                weekType = parseWeekType(cell.attrAny("data-week-type")),
                color = cell.attrAny("data-color").blankToNull(),
                note = cell.attrAny("data-note").blankToNull(),
                sourceText = cell.text().trim()
            )
        }
    }

    private fun parseMatrixTable(table: Element): List<CourseDraft> {
        val rows = table.select("tr")
        if (rows.size < 2) return emptyList()
        val headerCells = rows.first()?.select("th,td").orEmpty()
        val weekdayByColumn = headerCells.map { parseWeekday(it.text()) }

        val drafts = mutableListOf<CourseDraft>()
        rows.drop(1).forEach { row ->
            val cells = row.select("th,td")
            if (cells.size < 2) return@forEach
            val sectionRange = parseSectionRange(cells[0].text()) ?: return@forEach
            cells.drop(1).forEachIndexed { index, cell ->
                val text = cell.wholeText().replace('\u00A0', ' ').trim()
                if (text.isBlank()) return@forEachIndexed
                val weekday = weekdayByColumn.getOrNull(index + 1) ?: (index + 1).takeIf { it in 1..7 } ?: return@forEachIndexed
                parseCourseText(text, weekday, sectionRange.first, sectionRange.second)?.let(drafts::add)
            }
        }
        return drafts
    }

    private fun parseCourseText(text: String, weekday: Int, startSection: Int, endSection: Int): CourseDraft? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val name = lines.firstOrNull()?.removePrefix("课程：")?.trim().orEmpty()
        if (name.isBlank()) return null

        val teacher = lines.firstOrNull { it.contains("教师") || it.contains("老师") }
            ?.replace("教师：", "")
            ?.trim()
        val classroom = lines.firstOrNull { it.contains("教室") || it.matches(ROOM_PATTERN) }
            ?.replace("教室：", "")
            ?.trim()
        val weekRange = parseWeekRange(text)
        return CourseDraft(
            name = name,
            teacher = teacher.blankToNull(),
            classroom = classroom.blankToNull(),
            weekday = weekday,
            startSection = startSection,
            endSection = endSection,
            startWeek = weekRange?.first ?: 1,
            endWeek = weekRange?.second ?: 18,
            weekType = parseWeekType(text),
            sourceText = text
        )
    }

    private fun parseWeekday(text: String): Int? {
        return when {
            text.contains("一") || text.contains("Mon", ignoreCase = true) -> 1
            text.contains("二") || text.contains("Tue", ignoreCase = true) -> 2
            text.contains("三") || text.contains("Wed", ignoreCase = true) -> 3
            text.contains("四") || text.contains("Thu", ignoreCase = true) -> 4
            text.contains("五") || text.contains("Fri", ignoreCase = true) -> 5
            text.contains("六") || text.contains("Sat", ignoreCase = true) -> 6
            text.contains("日") || text.contains("天") || text.contains("Sun", ignoreCase = true) -> 7
            else -> null
        }
    }

    private fun parseSectionRange(text: String): Pair<Int, Int>? {
        val match = SECTION_PATTERN.find(text) ?: return null
        val start = match.groupValues[1].toInt()
        val end = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: start
        return Pair(start, end)
    }

    private fun parseWeekRange(text: String): Pair<Int, Int>? {
        val match = WEEK_PATTERN.find(text) ?: return null
        val start = match.groupValues[1].toInt()
        val end = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: start
        return Pair(start, end)
    }

    private fun parseWeekType(text: String): Int {
        return when {
            text.equals("1", ignoreCase = true) || text.contains("单") || text.contains("odd", ignoreCase = true) -> Course.WEEK_TYPE_ODD
            text.equals("2", ignoreCase = true) || text.contains("双") || text.contains("even", ignoreCase = true) -> Course.WEEK_TYPE_EVEN
            else -> Course.WEEK_TYPE_EVERY
        }
    }

    private fun Element.attrAny(vararg names: String): String {
        return names.firstNotNullOfOrNull { name -> attr(name).takeIf { it.isNotBlank() } }.orEmpty()
    }

    private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    companion object {
        private val SECTION_PATTERN = Regex("""第?\s*(\d+)\s*(?:[-~－到]\s*(\d+))?\s*节?""")
        private val WEEK_PATTERN = Regex("""第?\s*(\d+)\s*(?:[-~－到]\s*(\d+))?\s*周""")
        private val ROOM_PATTERN = Regex(""".*[A-Za-z]?\d{2,}.*""")
    }
}
