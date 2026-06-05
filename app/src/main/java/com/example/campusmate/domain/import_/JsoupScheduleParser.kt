package com.example.campusmate.domain.import_

import com.example.campusmate.data.model.Course
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Jsoup-based parser for fixture HTML and common table-shaped schedules. */
class JsoupScheduleParser : ScheduleParser {
    override fun parse(html: String): List<CourseDraft> {
        if (html.isBlank()) {
            throw ScheduleParseException("HTML 内容为空，无法导入课表。")
        }

        val document = Jsoup.parse(html)

        val drafts = buildList {
            // CampusMate fixture HTML can use explicit data-* attributes for stable parsing.
            document.selectFirst("table[data-campusmate-schedule]")?.let { addAll(parseCampusMateSampleTable(it)) }
            findBjtuScheduleTable(document)?.let { addAll(parseBjtuScheduleTable(it)) }
            if (isEmpty()) {
                // Fallback for pages that present schedule time in list/table cells (e.g. div.ellipsis title).
                addAll(parseBjtuEllipsisTitles(document))
            }
            if (isEmpty()) {
                // Generic fallback: try matrix schedule parsing on the first table.
                document.selectFirst("table")?.let { addAll(parseMatrixTable(it)) }
            }
        }.distinctBy { draftKey(it) }

        if (drafts.isEmpty()) {
            throw ScheduleParseException("未识别到课程。请确认页面包含课表表格或课程时间信息。")
        }

        return drafts
    }

    private fun parseCampusMateSampleTable(table: Element): List<CourseDraft> {
        val cells = table.select("td[data-course-name]")
        if (cells.isEmpty()) return emptyList()

        return cells.mapNotNull { cell ->
            val name = cell.attr("data-course-name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val weekday = cell.attr("data-weekday").toIntOrNull() ?: return@mapNotNull null
            val startSection = cell.attr("data-start-section").toIntOrNull() ?: return@mapNotNull null
            val endSection = cell.attr("data-end-section").toIntOrNull() ?: startSection
            val startWeek = cell.attr("data-start-week").toIntOrNull() ?: 1
            val endWeek = cell.attr("data-end-week").toIntOrNull() ?: startWeek
            val weekType = cell.attr("data-week-type").toIntOrNull() ?: Course.WEEK_TYPE_EVERY
            val teacher = cell.attr("data-teacher").trim().takeIf { it.isNotBlank() }
            val classroom = cell.attr("data-classroom").trim().takeIf { it.isNotBlank() }

            CourseDraft(
                name = name,
                teacher = teacher,
                classroom = classroom,
                weekday = weekday,
                startSection = startSection,
                endSection = endSection,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                sourceText = cell.text().trim()
            )
        }
    }

    private fun findBjtuScheduleTable(document: Document): Element? {
        val tables = document.select("table")
        return tables.firstOrNull { table ->
            val text = table.text()
            // BJTU schedule page usually contains "星期一" headers and "第1节" row labels.
            text.contains("星期一") && text.contains("第1节")
        } ?: tables.firstOrNull { table ->
            val text = table.text()
            // Some encodings or copies may still include "星期" and "第" patterns.
            text.contains("星期") && text.contains("第1节")
        }
    }

    private fun parseBjtuScheduleTable(table: Element): List<CourseDraft> {
        val rows = table.select("tr")
        if (rows.size < 2) return emptyList()

        val headerCells = rows.first()?.select("th,td").orEmpty()
        // Column 0 is the section label; columns 1..7 are weekdays.
        val weekdayByColumn = headerCells.map { parseWeekday(it.text()) }

        val drafts = mutableListOf<CourseDraft>()
        rows.drop(1).forEach { row ->
            val cells = row.select("th,td")
            if (cells.size < 2) return@forEach

            val sectionRange = parseSectionRange(cells[0].text()) ?: return@forEach
            cells.drop(1).forEachIndexed { index, cell ->
                if (cell.text().isBlank()) return@forEachIndexed

                val weekday = weekdayByColumn.getOrNull(index + 1)
                    ?: (index + 1).takeIf { it in 1..7 }
                    ?: return@forEachIndexed

                parseBjtuCell(cell, weekday, sectionRange.first, sectionRange.second)?.let(drafts::add)
            }
        }
        return drafts
    }

    private fun parseBjtuCell(cell: Element, weekday: Int, startSection: Int, endSection: Int): CourseDraft? {
        // Typical BJTU cell:
        // <span>CODE [01]<br>课程名<br></span>
        // <div>第01-16周 <i>教师</i></div>
        // <span class="text-muted">校区 楼 室</span>
        // <span class="green">[选中]</span>
        val name = extractBjtuCourseName(cell) ?: return null
        val teacher = cell.selectFirst("i")?.text()?.trim().takeIf { !it.isNullOrBlank() }
        val weekRange = extractWeekRange(cell)
        val weekType = parseWeekType(cell.text())
        val classroom = extractClassroom(cell)

        return CourseDraft(
            name = name,
            teacher = teacher,
            classroom = classroom,
            weekday = weekday,
            startSection = startSection,
            endSection = endSection,
            startWeek = weekRange?.first ?: 1,
            endWeek = weekRange?.second ?: 18,
            weekType = weekType,
            sourceText = cell.text().trim()
        )
    }

    private fun extractBjtuCourseName(cell: Element): String? {
        val span = cell.selectFirst("span") ?: return null
        val lines = span.wholeText()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        // Usually: [0]=code, [1]=course name.
        val candidate = lines.getOrNull(1) ?: lines.firstOrNull()
        val name = candidate?.trim().orEmpty()
        return name.takeIf { it.isNotBlank() }
    }

    private fun extractWeekRange(cell: Element): Pair<Int, Int>? {
        val container = cell.selectFirst("div[style*=max-width]") ?: cell.selectFirst("div")
        val text = container?.text().orEmpty()
        return parseWeekRange(text) ?: parseWeekRange(cell.text())
    }

    private fun extractClassroom(cell: Element): String? {
        val muted = cell.selectFirst("span.text-muted")?.text()?.trim().orEmpty()
        if (muted.isBlank()) return null
        // Prefer the last token if it looks like a room code (e.g. SY105, YF106).
        val token = muted.split(Regex("""[\s,，]+""")).lastOrNull()?.trim().orEmpty()
        return when {
            token.matches(ROOM_CODE_PATTERN) -> token
            else -> muted
        }
    }

    private fun parseBjtuEllipsisTitles(document: Document): List<CourseDraft> {
        // Some tables include time+location in <div class="ellipsis" title="第01-16周 星期二 第1节 海淀西校区, 思源楼, SY105">
        // If we can also find course name & teacher nearby in the same <tr>, we can build drafts.
        val drafts = mutableListOf<CourseDraft>()
        document.select("div.ellipsis[title]").forEach { div ->
            val title = div.attr("title").trim()
            val info = parseBjtuTitleInfo(title) ?: return@forEach

            val row = div.closest("tr")
            val name = row?.selectFirst("td")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { stripCourseCodePrefix(it) }

            val teacher = row?.select("div.ellipsis[title]")?.firstOrNull()?.attr("title")?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("第") }

            drafts += CourseDraft(
                name = name ?: "未命名课程",
                teacher = teacher,
                classroom = info.classroom,
                weekday = info.weekday,
                startSection = info.section,
                endSection = info.section,
                startWeek = info.startWeek,
                endWeek = info.endWeek,
                weekType = Course.WEEK_TYPE_EVERY,
                sourceText = title
            )
        }
        // Drop placeholder names if we have any real name entries.
        val hasRealName = drafts.any { it.name != "未命名课程" }
        return if (hasRealName) drafts.filter { it.name != "未命名课程" } else drafts
    }

    private data class BjtuTitleInfo(
        val startWeek: Int,
        val endWeek: Int,
        val weekday: Int,
        val section: Int,
        val classroom: String?
    )

    private fun parseBjtuTitleInfo(title: String): BjtuTitleInfo? {
        val weekRange = parseWeekRange(title) ?: return null
        val startWeek = weekRange.first
        val endWeek = weekRange.second

        val weekday = parseWeekday(title) ?: return null
        val section = SECTION_IN_TITLE_PATTERN.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null

        val classroom = title.split(Regex("""[,，]""")).lastOrNull()?.trim()?.takeIf { it.isNotBlank() }
        return BjtuTitleInfo(startWeek, endWeek, weekday, section, classroom)
    }

    private fun stripCourseCodePrefix(text: String): String {
        // e.g. "M302009B 数据库系统原理 01" -> "数据库系统原理"
        val normalized = text.replace('\u00A0', ' ').trim()
        val parts = normalized.split(Regex("""\s+"""))
        if (parts.size <= 1) return normalized
        // Drop first token if it looks like a course code.
        val first = parts.first()
        return if (first.matches(COURSE_CODE_PATTERN)) {
            parts.drop(1).joinToString(" ").replace(Regex("""\s+\d{1,2}$"""), "").trim()
        } else {
            normalized
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
                parseGenericCourseText(text, weekday, sectionRange.first, sectionRange.second)?.let(drafts::add)
            }
        }
        return drafts
    }

    private fun parseGenericCourseText(text: String, weekday: Int, startSection: Int, endSection: Int): CourseDraft? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val name = extractGenericCourseName(lines)
        if (name.isBlank()) return null
        val weekRange = parseWeekRange(text)
        return CourseDraft(
            name = name,
            teacher = extractGenericTeacher(text),
            classroom = extractGenericClassroom(text, lines),
            weekday = weekday,
            startSection = startSection,
            endSection = endSection,
            startWeek = weekRange?.first ?: 1,
            endWeek = weekRange?.second ?: 18,
            weekType = parseWeekType(text),
            sourceText = text
        )
    }

    private fun extractGenericCourseName(lines: List<String>): String {
        return lines.firstOrNull { line ->
            !isGenericMetadataLine(line)
        }?.trim().orEmpty()
    }

    private fun isGenericMetadataLine(line: String): Boolean {
        return line.contains("教师") ||
            line.contains("老师") ||
            line.contains("地点") ||
            line.contains("教室") ||
            line.contains("校区") ||
            line.contains("楼") ||
            line.contains("第") && line.contains("周") ||
            line.contains("单周") ||
            line.contains("双周") ||
            line.contains("Teacher", ignoreCase = true) ||
            line.contains("Room", ignoreCase = true) ||
            line.contains("Location", ignoreCase = true) ||
            line.contains("Venue", ignoreCase = true)
    }

    private fun extractGenericTeacher(text: String): String? {
        val match = GENERIC_TEACHER_PATTERN.find(text) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractGenericClassroom(text: String, lines: List<String>): String? {
        GENERIC_CLASSROOM_PATTERN.find(text)?.let { match ->
            val value = match.groupValues.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) return value
        }

        val locationLine = lines.firstOrNull { line ->
                line.contains("校区") ||
                line.contains("教学楼") ||
                line.contains("楼") && GENERIC_ROOM_TOKEN_PATTERN.containsMatchIn(line) ||
                line.contains("Room", ignoreCase = true) ||
                line.contains("Location", ignoreCase = true) ||
                line.contains("Venue", ignoreCase = true)
        }
        locationLine?.let { return stripGenericLocationLabel(it) }

        return GENERIC_ROOM_TOKEN_PATTERN.find(text)?.value
    }

    private fun stripGenericLocationLabel(text: String): String {
        return text.replace(
            Regex("""^(?:上课地点|地点|教室|校区|楼宇|教学楼|Venue|Location|Room)\s*[:：]?\s*""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
    }

    private fun parseWeekday(text: String): Int? {
        return when {
            text.contains("星期一") || text.contains("周一") || text.contains("Mon", ignoreCase = true) -> 1
            text.contains("星期二") || text.contains("周二") || text.contains("Tue", ignoreCase = true) -> 2
            text.contains("星期三") || text.contains("周三") || text.contains("Wed", ignoreCase = true) -> 3
            text.contains("星期四") || text.contains("周四") || text.contains("Thu", ignoreCase = true) -> 4
            text.contains("星期五") || text.contains("周五") || text.contains("Fri", ignoreCase = true) -> 5
            text.contains("星期六") || text.contains("周六") || text.contains("Sat", ignoreCase = true) -> 6
            text.contains("星期日") || text.contains("星期天") || text.contains("周日") || text.contains("Sun", ignoreCase = true) -> 7
            else -> null
        }
    }

    private fun parseSectionRange(text: String): Pair<Int, Int>? {
        val match = SECTION_RANGE_PATTERN.find(text) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: start
        return Pair(start, end)
    }

    private fun parseWeekRange(text: String): Pair<Int, Int>? {
        WEEK_RANGE_PATTERN.find(text)?.let { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return null
            val end = match.groupValues[2].toIntOrNull() ?: start
            return Pair(start, end)
        }
        WEEK_RANGE_WITHOUT_PREFIX_PATTERN.find(text)?.let { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return null
            val end = match.groupValues[2].toIntOrNull() ?: start
            return Pair(start, end)
        }
        WEEK_SINGLE_PATTERN.find(text)?.let { match ->
            val week = match.groupValues[1].toIntOrNull() ?: return null
            return Pair(week, week)
        }
        return null
    }

    private fun parseWeekType(text: String): Int {
        return when {
            text.contains("单周") || text.contains("odd", ignoreCase = true) -> Course.WEEK_TYPE_ODD
            text.contains("双周") || text.contains("even", ignoreCase = true) -> Course.WEEK_TYPE_EVEN
            else -> Course.WEEK_TYPE_EVERY
        }
    }

    private fun draftKey(draft: CourseDraft): String {
        return "${draft.name}-${draft.weekday}-${draft.startSection}-${draft.endSection}-${draft.startWeek}-${draft.endWeek}-${draft.weekType}"
    }

    companion object {
        private val SECTION_RANGE_PATTERN = Regex("""第?\s*(\d+)\s*节?(?:\s*[-~到]\s*(\d+)\s*节?)?""")
        private val SECTION_IN_TITLE_PATTERN = Regex("""第\s*(\d+)\s*节""")
        private val WEEK_RANGE_PATTERN = Regex("""第\s*(\d{1,2})\s*-\s*(\d{1,2})\s*周""")
        private val WEEK_RANGE_WITHOUT_PREFIX_PATTERN = Regex("""(?:周次\s*[:：]?\s*)?(\d{1,2})\s*[-~到]\s*(\d{1,2})\s*周""")
        private val WEEK_SINGLE_PATTERN = Regex("""第\s*(\d{1,2})\s*周""")
        private val COURSE_CODE_PATTERN = Regex("""^[A-Za-z]{1,4}\d{3,4}[A-Za-z]?$""")
        private val ROOM_CODE_PATTERN = Regex("""^[A-Za-z]{0,6}\d{2,4}$""")
        private val GENERIC_ROOM_TOKEN_PATTERN = Regex("""[A-Za-z]{0,6}\d{2,4}""")
        private val GENERIC_TEACHER_PATTERN =
            Regex("""(?:教师|老师|授课教师|任课教师|Teacher|Instructor)\s*[:：]?\s*([^\n;；,，]+)""", RegexOption.IGNORE_CASE)
        private val GENERIC_CLASSROOM_PATTERN =
            Regex("""(?:上课地点|地点|教室|校区|楼宇|教学楼|Venue|Location|Room)\s*[:：]?\s*([^\n;；]+)""", RegexOption.IGNORE_CASE)
    }
}
