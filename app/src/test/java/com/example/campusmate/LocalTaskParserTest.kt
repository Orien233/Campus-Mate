package com.example.campusmate

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.task.LocalTaskParser
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTaskParserTest {
    private val fixedNow = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        .parse("2026-06-04 10:00")!!
        .time

    @Test
    fun parse_extractsTaskFromHtmlListWhenAiIsUnavailable() {
        val result = LocalTaskParser { fixedNow }.parse(
            """
            {"title":"教学平台","text":"","html":"<ul><li>课程：数据库系统 作业：完成 ER 图设计 截止：2026-06-05 23:00</li></ul>"}
            """.trimIndent()
        )

        val task = result.drafts.single()
        assertEquals("完成 ER 图设计", task.title)
        assertEquals("数据库系统", task.courseName)
        assertEquals(StudyTask.TYPE_HOMEWORK, task.type)
        assertEquals(StudyTask.PRIORITY_HIGH, task.priority)
        assertEquals(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse("2026-06-05 23:00")!!.time,
            task.dueAt
        )
    }

    @Test
    fun parse_recognizesRelativeWeekdayAndTaskCategory() {
        val result = LocalTaskParser { fixedNow }.parse(
            "实验报告：完成网络实验第 2 部分；课程：计算机网络；截止：下周一 20:00；请尽快提交"
        )

        val task = result.drafts.single()
        assertEquals(StudyTask.TYPE_EXPERIMENT, task.type)
        assertEquals(StudyTask.PRIORITY_HIGH, task.priority)
        assertEquals(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse("2026-06-08 20:00")!!.time,
            task.dueAt
        )
    }

    @Test
    fun parse_ignoresNavigationOnlyLabels() {
        val result = runCatching {
            LocalTaskParser { fixedNow }.parse("<nav>首页 作业 课程 通知</nav>")
        }

        assertTrue(result.isFailure)
    }
}
