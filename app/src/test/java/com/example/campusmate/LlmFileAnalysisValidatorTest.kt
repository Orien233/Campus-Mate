package com.example.campusmate

import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.file.AI_FILE_SELECTION_REF
import com.example.campusmate.domain.ai.file.LlmFileAnalysisValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmFileAnalysisValidatorTest {
    private val validator = LlmFileAnalysisValidator()
    private val snapshot = dashboardAdviceSnapshot().copy(purpose = AiContextPurpose.FILE_ANALYSIS)

    @Test
    fun parseAndValidate_routesGroundedDraftsAndStripsPrivateFields() {
        val result = validator.parseAndValidate(VALID_RESPONSE, snapshot)
        val analysis = requireNotNull(result.analysis)

        assertEquals("文件包含一门课程、一个作业和两个学习安排。", analysis.summary)
        assertEquals(1, analysis.courses.size)
        assertEquals("算法设计", analysis.courses.single().draft.name)
        assertNull(analysis.courses.single().draft.teacher)
        assertNull(analysis.courses.single().draft.classroom)
        assertNull(analysis.courses.single().draft.note)
        assertNull(analysis.courses.single().draft.sourceText)
        assertEquals(listOf(AI_FILE_SELECTION_REF, "course:1"), analysis.courses.single().evidenceRefs)

        assertEquals(1, analysis.tasks.size)
        assertEquals("提交算法作业", analysis.tasks.single().draft.title)
        assertNull(analysis.tasks.single().draft.description)
        assertNull(analysis.tasks.single().draft.sourceText)
        assertTrue(analysis.tasks.single().draft.dueAt != null)

        assertEquals(1, analysis.plans.size)
        assertEquals("2026-06-08", analysis.plans.single().draft.planDate)
        assertEquals("10:00", analysis.plans.single().draft.startTime)
        assertTrue(analysis.warnings.any { it.contains("已有安排冲突") })

        assertEquals(listOf("course:1"), analysis.insights.single().localRefs)
        assertEquals(1, analysis.localMatches.size)
        assertEquals("course:1", analysis.localMatches.single().localRef)
        assertTrue(analysis.localMatches.single().evidenceRefs.contains(AI_FILE_SELECTION_REF))
        assertFalse(analysis.summary.contains("Activity"))
    }

    @Test
    fun parseAndValidate_dropsUngroundedAndUnknownReferences() {
        val raw = """
            {
              "schemaVersion": 1,
              "summary": "只保留有文件证据且本地引用有效的结果。",
              "courses": [{
                "name": "虚构课程", "weekday": 2,
                "startSection": 1, "endSection": 2,
                "startWeek": 1, "endWeek": 18, "weekType": 0,
                "evidenceRefs": ["course:1"]
              }],
              "tasks": [{
                "title": "无依据任务", "type": "homework", "priority": "normal",
                "evidenceRefs": ["unknown:9"]
              }],
              "plans": [],
              "insights": [{
                "title": "可保留要点", "detail": "来自文件本身。",
                "evidenceRefs": ["file:selection:1"],
                "localRefs": ["unknown:9", "task:3"]
              }],
              "localMatches": [
                {"localRef":"unknown:9","reason":"猜测","evidenceRefs":["file:selection:1"]},
                {"localRef":"task:3","reason":"标题相符","evidenceRefs":["file:selection:1"]}
              ]
            }
        """.trimIndent()

        val analysis = requireNotNull(validator.parseAndValidate(raw, snapshot).analysis)

        assertTrue(analysis.courses.isEmpty())
        assertTrue(analysis.tasks.isEmpty())
        assertEquals(listOf("task:3"), analysis.insights.single().localRefs)
        assertEquals(listOf("task:3"), analysis.localMatches.map { it.localRef })
        assertTrue(analysis.warnings.any { it.contains("缺少文件依据") })
        assertTrue(analysis.warnings.any { it.contains("本地关联") })
    }

    @Test
    fun parseAndValidate_rejectsWrongSchemaOrMissingSummary() {
        val wrongSchema = validator.parseAndValidate(
            """{"schemaVersion":2,"summary":"x"}""",
            snapshot
        )
        assertNull(wrongSchema.analysis)
        assertTrue(wrongSchema.warnings.any { it.contains("schemaVersion") })

        val noSummary = validator.parseAndValidate(
            """{"schemaVersion":1,"summary":"","courses":[]}""",
            snapshot
        )
        assertNull(noSummary.analysis)

        val wrongPurpose = validator.parseAndValidate(VALID_RESPONSE, dashboardAdviceSnapshot())
        assertNull(wrongPurpose.analysis)
    }

    companion object {
        private val VALID_RESPONSE = """
            {
              "schemaVersion": 1,
              "summary": "文件包含一门课程、一个作业和两个学习安排。",
              "keyPoints": ["先确认课程周次", "作业截止前完成"],
              "courses": [{
                "name": "算法设计",
                "teacher": "不应保留的教师",
                "classroom": "不应保留的教室",
                "note": "不应保留的备注",
                "sourceText": "不应保留的原文",
                "weekday": 3,
                "startSection": 3,
                "endSection": 4,
                "startWeek": 1,
                "endWeek": 16,
                "weekType": 0,
                "evidenceRefs": ["file:selection:1", "course:1", "unknown:9"]
              }],
              "tasks": [{
                "title": "提交算法作业",
                "description": "不应保留的任务描述",
                "rawText": "不应保留的原文",
                "courseName": "算法设计",
                "type": "homework",
                "priority": "high",
                "dueAt": "2026-06-09 18:00",
                "remindAt": "2026-06-09 17:00",
                "evidenceRefs": ["file:selection:1"]
              }],
              "plans": [
                {
                  "title": "整理算法笔记",
                  "planDate": "2026-06-08",
                  "plannedMinutes": 60,
                  "startTime": "10:00",
                  "endTime": "11:00",
                  "evidenceRefs": ["file:selection:1"]
                },
                {
                  "title": "冲突安排",
                  "planDate": "2026-06-08",
                  "plannedMinutes": 30,
                  "startTime": "08:30",
                  "endTime": "09:00",
                  "evidenceRefs": ["file:selection:1"]
                },
                {
                  "title": "与文件计划冲突",
                  "planDate": "2026-06-08",
                  "plannedMinutes": 30,
                  "startTime": "10:30",
                  "endTime": "11:00",
                  "evidenceRefs": ["file:selection:1"]
                }
              ],
              "insights": [{
                "title": "学习提示",
                "detail": "文件中的重点与现有课程相关。",
                "evidenceRefs": ["file:selection:1"],
                "localRefs": ["course:1", "unknown:9"]
              }],
              "localMatches": [
                {
                  "localRef": "course:1",
                  "reason": "课程名称相关",
                  "evidenceRefs": ["file:selection:1"]
                },
                {
                  "localRef": "unknown:9",
                  "reason": "不允许的引用",
                  "evidenceRefs": ["file:selection:1"]
                }
              ],
              "warnings": ["课程周次需要用户确认"],
              "action": "openActivity",
              "url": "https://example.invalid"
            }
        """.trimIndent()
    }
}
