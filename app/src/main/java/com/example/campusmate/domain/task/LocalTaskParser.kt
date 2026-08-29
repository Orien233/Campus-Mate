package com.example.campusmate.domain.task

import com.example.campusmate.data.model.StudyTask
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Conservative on-device parser for common assignment, notice, and task-list pages.
 * It only emits a draft when a text fragment has a concrete learning-task signal.
 */
class LocalTaskParser(
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun parse(pageContent: String): LlmTaskDraftValidationResult {
        val document = Jsoup.parse(extractHtml(pageContent))
        val candidates = linkedSetOf<String>()

        document.select("tr, li, article, [data-task], [data-assignment], .task, .assignment, .homework, .notice")
            .forEach { element -> element.wholeText().normalizeFragment()?.let(candidates::add) }

        document.body()?.wholeText()
            ?.lines()
            ?.mapNotNull { it.normalizeFragment() }
            ?.filter(::hasTaskSignal)
            ?.forEach(candidates::add)

        extractText(pageContent)
            .lines()
            .mapNotNull { it.normalizeFragment() }
            .filter(::hasTaskSignal)
            .forEach(candidates::add)

        val warnings = mutableListOf<String>()
        val drafts = candidates.mapNotNull { candidate ->
            parseCandidate(candidate, warnings)
        }.distinctBy(::draftKey)

        if (drafts.isEmpty()) {
            throw TaskParseException("本地规则未识别到明确的学习任务，请确认页面包含作业、实验、考试或截止信息。")
        }
        return LlmTaskDraftValidationResult(drafts, warnings.distinct())
    }

    private fun parseCandidate(text: String, warnings: MutableList<String>): TaskDraft? {
        if (!hasTaskSignal(text) || isNavigationText(text)) return null
        val type = detectType(text)
        val title = extractTitle(text) ?: return null
        if (title.length < MIN_TITLE_LENGTH || title.length > MAX_TITLE_LENGTH) return null

        val dueText = DUE_VALUE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: DATE_TIME_PATTERN.find(text)?.value
        val dueAt = dueText?.let(::parseTime)
        if (DUE_VALUE_PATTERN.containsMatchIn(text) && dueAt == null) {
            warnings += "未能识别“${title.take(24)}”的截止时间，已保留任务标题。"
        }

        return TaskDraft(
            title = title,
            description = text.take(MAX_DESCRIPTION_LENGTH).takeIf { it != title },
            courseName = COURSE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.cleanValue(),
            type = type,
            priority = detectPriority(text, dueAt),
            dueAt = dueAt,
            sourceText = text.take(MAX_SOURCE_LENGTH)
        )
    }

    private fun extractTitle(text: String): String? {
        TITLE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.cleanTitle()?.let { return it }
        val lines = text.split('\n', '；', ';').mapNotNull { it.normalizeFragment() }
        val candidate = lines.firstOrNull { line ->
            hasTaskSignal(line) &&
                !COURSE_PATTERN.containsMatchIn(line) &&
                !DUE_VALUE_PATTERN.containsMatchIn(line) &&
                !isNavigationText(line)
        } ?: lines.firstOrNull { hasTaskSignal(it) && !isNavigationText(it) }
        return candidate?.cleanTitle()
    }

    private fun String.cleanTitle(): String? {
        return replace(TITLE_PREFIX_PATTERN, "")
            .replace(DUE_VALUE_PATTERN, "")
            .replace(COURSE_PATTERN, "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '：', ':', '-', '—', '，', ',')
            .take(MAX_TITLE_LENGTH)
            .takeIf { it.isNotBlank() }
    }

    private fun String.cleanValue(): String? {
        return trim().trim(' ', '：', ':', '，', ',').take(60).takeIf { it.isNotBlank() }
    }

    private fun hasTaskSignal(text: String): Boolean {
        return TASK_SIGNAL_PATTERN.containsMatchIn(text) ||
            (DUE_VALUE_PATTERN.containsMatchIn(text) && text.length >= MIN_TITLE_LENGTH)
    }

    private fun isNavigationText(text: String): Boolean {
        return text.length <= 30 && NAVIGATION_PATTERN.matches(text.trim())
    }

    private fun detectType(text: String): Int = when {
        Regex("""考试|测验|quiz|exam|期中|期末""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> StudyTask.TYPE_EXAM
        Regex("""实验|lab|experiment|实验报告""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> StudyTask.TYPE_EXPERIMENT
        Regex("""项目|大作业|课程设计|project""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> StudyTask.TYPE_PROJECT
        Regex("""复习|预习|revision|review""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> StudyTask.TYPE_REVIEW
        Regex("""作业|练习|习题|提交|homework|assignment""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> StudyTask.TYPE_HOMEWORK
        else -> StudyTask.TYPE_OTHER
    }

    private fun detectPriority(text: String, dueAt: Long?): Int {
        if (Regex("""紧急|重要|尽快|必做|urgent|important""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return StudyTask.PRIORITY_HIGH
        }
        val now = nowMillisProvider()
        if (dueAt != null && dueAt in now..(now + HIGH_PRIORITY_WINDOW_MILLIS)) {
            return StudyTask.PRIORITY_HIGH
        }
        if (Regex("""可选|选做|optional""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return StudyTask.PRIORITY_LOW
        }
        return StudyTask.PRIORITY_NORMAL
    }

    private fun parseTime(raw: String): Long? {
        val text = raw.trim().replace("截止", "").replace("提交", "").trim()
        if (text.isBlank()) return null
        parseRelativeDay(text)?.let { return it }
        parseRelativeWeekday(text)?.let { return it }
        DATE_TIME_FORMATS.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }.parse(text)?.time
            }.getOrNull()?.let { return it }
        }
        MONTH_DAY_PATTERN.find(text)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val hour = match.groupValues[3].toIntOrNull() ?: 23
            val minute = match.groupValues[4].toIntOrNull() ?: 59
            return Calendar.getInstance().apply {
                timeInMillis = nowMillisProvider()
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis < nowMillisProvider() - ONE_DAY_MILLIS) add(Calendar.YEAR, 1)
            }.timeInMillis
        }
        return null
    }

    private fun parseRelativeDay(text: String): Long? {
        val offset = when {
            text.contains("后天") -> 2
            text.contains("明天") -> 1
            text.contains("今天") || text.contains("今日") || text.contains("今晚") -> 0
            else -> return null
        }
        return calendarForOffset(offset, timeParts(text))
    }

    private fun parseRelativeWeekday(text: String): Long? {
        val match = Regex("""(本周|这周|下周)?\s*(?:星期|周)([一二三四五六日天])""").find(text) ?: return null
        val target = "一二三四五六日".indexOf(match.groupValues[2].replace('天', '日')) + 1
        if (target !in 1..7) return null
        val now = Calendar.getInstance().apply { timeInMillis = nowMillisProvider() }
        val current = ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        var offset = target - current
        if (match.groupValues[1] == "下周") offset += 7
        else if (match.groupValues[1].isBlank() && offset < 0) offset += 7
        return calendarForOffset(offset, timeParts(text))
    }

    private fun calendarForOffset(offset: Int, time: Pair<Int, Int>): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillisProvider()
            add(Calendar.DAY_OF_MONTH, offset)
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun timeParts(text: String): Pair<Int, Int> {
        val match = TIME_PATTERN.find(text)
        return (match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 23).coerceIn(0, 23) to
            (match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 59).coerceIn(0, 59)
    }

    private fun extractHtml(pageContent: String): String {
        return runCatching { JSONObject(pageContent).optString("html") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: pageContent
    }

    private fun extractText(pageContent: String): String {
        return runCatching { JSONObject(pageContent).optString("text") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Jsoup.parse(pageContent).text()
    }

    private fun String.normalizeFragment(): String? =
        replace('\u00A0', ' ').replace(Regex("""[ \t]+"""), " ").trim().takeIf { it.isNotBlank() }

    private fun draftKey(draft: TaskDraft): String = listOf(
        draft.title.lowercase(Locale.ROOT), draft.courseName.orEmpty().lowercase(Locale.ROOT), draft.dueAt ?: 0L
    ).joinToString("|")

    companion object {
        private const val MIN_TITLE_LENGTH = 3
        private const val MAX_TITLE_LENGTH = 120
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val MAX_SOURCE_LENGTH = 800
        private const val HIGH_PRIORITY_WINDOW_MILLIS = 3 * 24 * 60 * 60 * 1000L
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private val TASK_SIGNAL_PATTERN = Regex("""作业|实验|考试|测验|复习|预习|项目|大作业|课程设计|提交|截止|homework|assignment|experiment|\blab\b|\bexam\b|\bquiz\b|\breview\b|\bproject\b""", RegexOption.IGNORE_CASE)
        private val NAVIGATION_PATTERN = Regex("""(?:首页|作业|任务|课程|通知|全部|已完成|待完成|我的作业)(?:[\s|/、>]+(?:首页|作业|任务|课程|通知|全部|已完成|待完成|我的作业))*""")
        private val TITLE_PATTERN = Regex("""(?:任务|作业|实验|考试|项目)(?:名称|标题|内容|题目)?\s*[:：]\s*([^\n；;]+)""")
        private val TITLE_PREFIX_PATTERN = Regex("""^(?:任务|作业|实验|考试|项目)(?:名称|标题|内容|题目)?\s*[:：]?\s*""")
        private val COURSE_PATTERN = Regex("""(?:课程|科目|课程名称|所属课程)\s*[:：]\s*([^\n；;,，]+?)(?=\s*(?:任务|作业|实验|考试|项目)(?:名称|标题|内容|题目)?\s*[:：]|[\n；;,，]|$)""")
        private val DUE_VALUE_PATTERN = Regex("""(?:截止(?:时间|日期)?|提交(?:截止)?|due(?:\s*date|\s*time)?|deadline)\s*[:：]?\s*([^\n；;，,]+)""", RegexOption.IGNORE_CASE)
        private val DATE_TIME_PATTERN = Regex("""(?:\d{4}[/-]\d{1,2}[/-]\d{1,2}|\d{4}年\d{1,2}月\d{1,2}日|\d{1,2}月\d{1,2}日|(?:今天|明天|后天|本周|这周|下周)?\s*(?:星期|周)[一二三四五六日天])(?:\s*(?:\d{1,2}[:：]\d{1,2}))?""")
        private val TIME_PATTERN = Regex("""(\d{1,2})[:：](\d{1,2})""")
        private val MONTH_DAY_PATTERN = Regex("""(\d{1,2})月(\d{1,2})日?(?:\s*(\d{1,2})[:：](\d{1,2}))?""")
        private val DATE_TIME_FORMATS = listOf(
            "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "yyyy年M月d日 HH:mm",
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy年M月d日"
        )
    }
}
