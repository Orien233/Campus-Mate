package com.example.campusmate

import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.domain.ai.command.AiCourseSnapshot
import com.example.campusmate.domain.ai.command.AiPlanSnapshot
import com.example.campusmate.domain.ai.command.AiRecordCommandContext
import com.example.campusmate.domain.ai.command.AiRecordCommandValidator
import com.example.campusmate.domain.ai.command.AiRecordOperation
import com.example.campusmate.domain.ai.command.AiTaskSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecordCommandValidatorTest {
    private val validator = AiRecordCommandValidator()
    private val course = AiCourseSnapshot(
        1, "高等数学", "张老师", "A101", 1, 1, 2, 1, 18, 0, "#112233", "旧备注", 100
    )
    private val task = AiTaskSnapshot(
        2, 1, "复习第三章", "旧描述", 3, 1, null, null, StudyTask.STATUS_TODO, 200
    )
    private val plan = AiPlanSnapshot(
        3, "晚间复习", "2026-09-03", 60, "19:00", "20:00", 0, 0, 0, 2, 300
    )
    private val context = AiRecordCommandContext(500, listOf(course), listOf(task), listOf(plan))

    @Test
    fun update_mergesOnlyPresentFields_andExplicitNullClearsNullableField() {
        val input = "把高等数学教室改为A102，并清空备注"
        val result = validator.parseAndValidate(
            """
            {"schemaVersion":1,"changes":[{
              "recordType":"course","operation":"update","targetRef":"course:1",
              "evidenceQuote":"把高等数学教室改为A102，并清空备注",
              "fields":{"classroom":"A102","note":null}
            }],"warnings":[]}
            """.trimIndent(),
            input,
            context
        )
        assertEquals(1, result.changes.size)
        val after = result.changes.single().after as AiCourseSnapshot
        assertEquals("高等数学", after.name)
        assertEquals("张老师", after.teacher)
        assertEquals("A102", after.classroom)
        assertNull(after.note)
        assertEquals(100L, result.changes.single().expectedUpdatedAt)
    }

    @Test
    fun statusUpdate_isAccepted_andDeleteNeedsExplicitIntent() {
        val input = "把复习第三章标记完成；高等数学不要了"
        val raw = """
            {"schemaVersion":1,"changes":[
              {"recordType":"task","operation":"update","targetRef":"task:2",
               "evidenceQuote":"把复习第三章标记完成","fields":{"status":1}},
              {"recordType":"course","operation":"delete","targetRef":"course:1",
               "evidenceQuote":"高等数学不要了","fields":{}}
            ],"warnings":[]}
        """.trimIndent()
        val result = validator.parseAndValidate(raw, input, context)
        assertEquals(1, result.changes.size)
        assertEquals(StudyTask.STATUS_DONE, (result.changes.single().after as AiTaskSnapshot).status)
        assertTrue(result.warnings.any { it.contains("删除意图") })
    }

    @Test
    fun delete_isUnchecked_duplicateTargetKeepsFirst_andBadRefIsDropped() {
        val input = "删除高等数学，再删除高等数学，并删除不存在课程"
        val raw = """
            {"schemaVersion":1,"changes":[
              {"recordType":"course","operation":"delete","targetRef":"course:1","evidenceQuote":"删除高等数学","fields":{}},
              {"recordType":"course","operation":"delete","targetRef":"course:1","evidenceQuote":"再删除高等数学","fields":{}},
              {"recordType":"course","operation":"delete","targetRef":"course:99","evidenceQuote":"删除不存在课程","fields":{}}
            ],"warnings":[]}
        """.trimIndent()
        val result = validator.parseAndValidate(raw, input, context)
        assertEquals(1, result.changes.size)
        assertEquals(AiRecordOperation.DELETE, result.changes.single().operation)
        assertFalse(result.changes.single().defaultSelected)
        assertTrue(result.warnings.any { it.contains("重复") })
        assertTrue(result.warnings.any { it.contains("允许范围") })
    }

    @Test
    fun evidenceOutsideOriginal_andIllegalFieldAreDropped() {
        val input = "修改复习任务"
        val raw = """
            {"schemaVersion":1,"changes":[
              {"recordType":"task","operation":"update","targetRef":"task:2","evidenceQuote":"不存在的证据","fields":{"title":"新标题"}},
              {"recordType":"task","operation":"update","targetRef":"task:2","evidenceQuote":"修改复习任务","fields":{"updatedAt":99}}
            ],"warnings":[]}
        """.trimIndent()
        val result = validator.parseAndValidate(raw, input, context)
        assertTrue(result.changes.isEmpty())
        assertTrue(result.warnings.any { it.contains("证据") })
        assertTrue(result.warnings.any { it.contains("不允许修改") })
    }

    @Test
    fun createCourseWithConflict_isValidButUnchecked() {
        val input = "新增线性代数，周一第1到2节，第1到18周"
        val raw = """
            {"schemaVersion":1,"changes":[{
              "recordType":"course","operation":"create",
              "evidenceQuote":"新增线性代数，周一第1到2节，第1到18周",
              "fields":{"name":"线性代数","weekday":1,"startSection":1,"endSection":2,"startWeek":1,"endWeek":18}
            }],"warnings":[]}
        """.trimIndent()
        val change = validator.parseAndValidate(raw, input, context).changes.single()
        assertTrue(change.hasTimeConflict)
        assertFalse(change.defaultSelected)
    }

    @Test
    fun moreThanThirtyChanges_areCapped() {
        val input = "新增任务"
        val items = (1..35).joinToString(",") {
            """{"recordType":"task","operation":"create","evidenceQuote":"新增任务","fields":{"title":"任务$it"}}"""
        }
        val result = validator.parseAndValidate(
            """{"schemaVersion":1,"changes":[$items],"warnings":[]}""", input, context
        )
        assertEquals(30, result.changes.size)
        assertTrue(result.warnings.any { it.contains("30") })
    }
}
