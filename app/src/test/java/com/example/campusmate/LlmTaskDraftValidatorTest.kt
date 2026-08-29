package com.example.campusmate

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.task.LlmTaskDraftValidator
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTaskDraftValidatorTest {
    private val fixedNow = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        .parse("2026-06-04 10:00")!!
        .time

    @Test
    fun parse_readsTaskDraftFields() {
        val result = LlmTaskDraftValidator { fixedNow }.parse(
            """
            {
              "tasks": [
                {
                  "title": "完成数据库实验报告",
                  "description": "提交到教学平台",
                  "courseName": "数据库系统",
                  "type": "experiment",
                  "priority": "high",
                  "dueAt": "2026-06-05 23:00",
                  "remindAt": "明天 20:00"
                }
              ]
            }
            """.trimIndent()
        )

        val draft = result.drafts.single()
        assertEquals("完成数据库实验报告", draft.title)
        assertEquals("数据库系统", draft.courseName)
        assertEquals(StudyTask.TYPE_EXPERIMENT, draft.type)
        assertEquals(StudyTask.PRIORITY_HIGH, draft.priority)
        assertEquals(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse("2026-06-05 23:00")!!.time,
            draft.dueAt
        )
        assertEquals(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse("2026-06-05 20:00")!!.time,
            draft.remindAt
        )
    }

    @Test
    fun parse_skipsItemsWithoutTitle() {
        val result = LlmTaskDraftValidator { fixedNow }.parse(
            """
            {
              "tasks": [
                {"type": "homework"},
                {"title": "复习高数", "type": "review", "priority": "normal"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.drafts.size)
        assertEquals(StudyTask.TYPE_REVIEW, result.drafts.first().type)
        assertTrue(result.warnings.any { it.contains("缺少标题") })
    }

    @Test
    fun parse_readsMultipleTaskDrafts() {
        val result = LlmTaskDraftValidator { fixedNow }.parse(
            """
            {
              "tasks": [
                {"title": "完成实验报告", "type": "experiment", "priority": "high"},
                {"title": "复习高等数学", "type": "review", "priority": "normal"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, result.drafts.size)
        assertEquals("完成实验报告", result.drafts[0].title)
        assertEquals("复习高等数学", result.drafts[1].title)
    }

    @Test
    fun parse_acceptsCommonAliasesAndRelativeWeekday() {
        val result = LlmTaskDraftValidator { fixedNow }.parse(
            """
            {
              "tasks": [
                {
                  "task_name": "提交网络实验报告",
                  "course": "计算机网络",
                  "category": "课程实验",
                  "urgency": "非常重要",
                  "due_time": "下周一 20:00"
                }
              ]
            }
            """.trimIndent()
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
    fun parse_keepsTaskAndWarnsForMalformedOptionalDeadline() {
        val result = LlmTaskDraftValidator { fixedNow }.parse(
            """{"tasks":[{"title":"完成作业","type":"homework","deadline":"月底之前"}]}"""
        )

        assertEquals(1, result.drafts.size)
        assertEquals(null, result.drafts.single().dueAt)
        assertTrue(result.warnings.any { it.contains("截止时间格式") })
    }
}
