package com.example.campusmate

import com.example.campusmate.data.model.Course
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.domain.plan.LlmPlanValidator
import com.example.campusmate.domain.plan.StudyPlanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class LlmPlanValidatorTest {
    @Test
    fun parseAndValidate_skipsPlansBeforeGenerationWindow() {
        val result = LlmPlanValidator().parseAndValidate(
            jsonContent = """
                {
                  "plans": [
                    {
                      "title": "复习线性代数",
                      "plannedMinutes": 60,
                      "startTime": "09:00",
                      "endTime": "10:00",
                      "type": 1,
                      "sourceType": 2
                    }
                  ]
                }
            """.trimIndent(),
            planContext = context(generationStartTime = "15:30")
        )

        assertTrue(result.plans.isEmpty())
        assertTrue(result.warnings.any { it.contains("不在允许生成时间") })
    }

    @Test
    fun parseAndValidate_acceptsPlansInsideGenerationWindow() {
        val result = LlmPlanValidator().parseAndValidate(
            jsonContent = """
                {
                  "plans": [
                    {
                      "title": "复习线性代数",
                      "plannedMinutes": 60,
                      "startTime": "16:00",
                      "endTime": "17:00",
                      "type": 1,
                      "sourceType": 2
                    }
                  ]
                }
            """.trimIndent(),
            planContext = context(generationStartTime = "15:30")
        )

        assertEquals(1, result.plans.size)
        assertEquals("16:00", result.plans.first().startTime)
    }

    @Test
    fun parseAndValidate_acceptsFencedJsonAndUsesLocalMetadata() {
        val result = LlmPlanValidator().parseAndValidate(
            jsonContent = """
                模型说明
                ```json
                {
                  "plans": [
                    {
                      "title": "完成数据库项目",
                      "plannedMinutes": 60,
                      "startTime": "16:00",
                      "endTime": "17:00",
                      "type": 999,
                      "sourceType": 999
                    }
                  ],
                  "warnings": ["已优先安排临近截止任务"]
                }
                ```
            """.trimIndent(),
            planContext = context(generationStartTime = "15:30"),
            outputPlanType = StudyPlan.TYPE_WEEKLY
        )

        assertEquals(1, result.plans.size)
        assertEquals(StudyPlan.TYPE_WEEKLY, result.plans.first().type)
        assertEquals(StudyPlan.SOURCE_LLM, result.plans.first().sourceType)
        assertTrue(result.warnings.contains("已优先安排临近截止任务"))
    }

    @Test
    fun parseAndValidate_acceptsSelectedMemoryButKeepsLocalMetadata() {
        val result = LlmPlanValidator().parseAndValidate(
            jsonContent = response(planItem().put("memoryRefs", JSONArray().put("memory:7"))
                .put("type", 999).put("sourceType", 999)),
            planContext = planRagContext(planRagMemoryContext()),
            outputPlanType = StudyPlan.TYPE_WEEKLY
        )

        assertEquals(1, result.plans.size)
        assertEquals(StudyPlan.TYPE_WEEKLY, result.plans.single().type)
        assertEquals(StudyPlan.SOURCE_LLM, result.plans.single().sourceType)
    }

    @Test
    fun parseAndValidate_rejectsWholeItemForUnknownOrMalformedMemoryRefs() {
        val invalidRefs = listOf(
            JSONArray().put("memory:999"),
            JSONArray().put("course:7"),
            JSONArray().put(7),
            JSONArray().put("memory:7").put(false),
            JSONObject.NULL,
            "memory:7"
        )
        invalidRefs.forEach { refs ->
            val result = LlmPlanValidator().parseAndValidate(
                response(planItem().put("memoryRefs", refs)),
                planRagContext(planRagMemoryContext())
            )
            assertTrue(result.plans.isEmpty())
            assertTrue(result.warnings.any { it.contains("记忆引用") })
        }
    }

    @Test
    fun parseAndValidate_memoryMustBeInThisRequestsContext() {
        val raw = response(planItem().put("memoryRefs", JSONArray().put("memory:7")))
        val disabledOrExpiredContext = planRagContext()

        assertTrue(LlmPlanValidator().parseAndValidate(raw, disabledOrExpiredContext).plans.isEmpty())
        assertTrue(LlmPlanValidator().parseAndValidate(raw, "2026-06-05").plans.isEmpty())
    }

    @Test
    fun parseAndValidate_acceptsOmittedOrEmptyMemoryRefsForLegacyResponses() {
        val raw = response(
            planItem(title = "第一项"),
            planItem(title = "第二项", start = "17:00", end = "18:00")
                .put("memoryRefs", JSONArray())
        )

        assertEquals(2, LlmPlanValidator().parseAndValidate(raw, planRagContext()).plans.size)
    }

    @Test
    fun parseAndValidate_memoryCannotOverrideCurrentCourseOrGenerationWindow() {
        val course = Course(id = 1L, name = "线性代数", weekday = 5, startSection = 1, endSection = 2)
        val base = planRagContext(planRagMemoryContext())
        val raw = response(planItem().put("memoryRefs", JSONArray().put("memory:7")))
        val occupiedContext = base.copy(
            courses = listOf(course),
            courseTimeRanges = mapOf(1L to "16:00-17:00")
        )

        assertTrue(LlmPlanValidator().parseAndValidate(raw, occupiedContext).plans.isEmpty())
        assertTrue(LlmPlanValidator().parseAndValidate(
            raw, base.copy(generationStartTime = "17:00")
        ).plans.isEmpty())
    }

    @Test
    fun parseAndValidate_rejectsExistingPlanOverlapButAllowsTouchingBoundary() {
        val existing = StudyPlan(
            title = "已有安排", planDate = "2026-06-05", plannedMinutes = 60,
            startTime = "16:00", endTime = "17:00"
        )
        val result = LlmPlanValidator().parseAndValidate(
            response(
                planItem(title = "冲突", start = "16:30", end = "17:30")
                    .put("memoryRefs", JSONArray().put("memory:7")),
                planItem(title = "相邻", start = "17:00", end = "18:00")
            ),
            planRagContext(planRagMemoryContext()).copy(existingPlans = listOf(existing))
        )

        assertEquals(listOf("相邻"), result.plans.map { it.title })
        assertTrue(result.warnings.any { it.contains("与已有计划时间冲突") })
    }

    @Test
    fun parseAndValidate_rejectsOverlapsWithinNewPlansButKeepsValidItems() {
        val result = LlmPlanValidator().parseAndValidate(
            response(
                planItem(title = "第一项"),
                planItem(title = "重叠项", start = "16:30", end = "17:30"),
                planItem(title = "相邻项", start = "17:00", end = "18:00")
            ),
            planRagContext()
        )

        assertEquals(listOf("第一项", "相邻项"), result.plans.map { it.title })
        assertTrue(result.warnings.any { it.contains("与本次其他计划时间冲突") })
    }

    @Test
    fun parseAndValidate_requiresMatchingIntegerDurationAndStrictTimes() {
        val invalidItems = listOf(
            planItem().put("plannedMinutes", 45),
            planItem().put("plannedMinutes", 60.5),
            planItem().put("plannedMinutes", "60"),
            planItem(start = "16:00extra"),
            planItem(end = "24:00"),
            planItem(end = "16:00")
        )
        invalidItems.forEach { item ->
            assertTrue(LlmPlanValidator().parseAndValidate(
                response(item), planRagContext()
            ).plans.isEmpty())
        }
    }

    @Test
    fun parseAndValidate_keepsCourseLearningExceptionInsideCourseSlot() {
        val course = Course(id = 1L, name = "线性代数", weekday = 5, startSection = 1, endSection = 2)
        val context = planRagContext().copy(
            courses = listOf(course),
            courseTimeRanges = mapOf(1L to "16:00-17:00")
        )
        val result = LlmPlanValidator().parseAndValidate(
            response(planItem(title = "上课：线性代数")),
            context
        )

        assertEquals(1, result.plans.size)
    }

    private fun planItem(
        title: String = "复习线性代数",
        start: String = "16:00",
        end: String = "17:00"
    ): JSONObject = JSONObject()
        .put("title", title)
        .put("plannedMinutes", 60)
        .put("startTime", start)
        .put("endTime", end)

    private fun response(vararg plans: JSONObject): String = JSONObject()
        .put("plans", JSONArray().apply { plans.forEach { put(it) } })
        .toString()

    private fun context(generationStartTime: String): StudyPlanContext {
        return StudyPlanContext(
            date = "2026-06-05",
            weekday = 5,
            weekdayName = "周五",
            dailyGoalMinutes = 120,
            courses = emptyList(),
            tasks = emptyList(),
            weather = null,
            recentStudyRecords = emptyList(),
            existingPlans = emptyList(),
            coursesById = emptyMap(),
            courseTimeRanges = emptyMap(),
            planEarliestTime = "08:00",
            planLatestTime = "22:00",
            generationStartTime = generationStartTime
        )
    }
}
