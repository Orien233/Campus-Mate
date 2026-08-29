package com.example.campusmate

import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.domain.plan.LlmPlanValidator
import com.example.campusmate.domain.plan.StudyPlanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
