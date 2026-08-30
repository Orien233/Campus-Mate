package com.example.campusmate

import com.example.campusmate.domain.ai.advice.AiAdvicePriority
import com.example.campusmate.domain.ai.advice.LlmDashboardAdviceValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmDashboardAdviceValidatorTest {
    private val validator = LlmDashboardAdviceValidator()

    @Test
    fun parseAndValidate_filtersEvidenceAndConflictingTimes() {
        val raw = """
            下面是建议：
            \`\`\`json
            {
              "headline": "先完成临期实验",
              "summary": "上午课程后优先处理实验，再安排短时复习。",
              "items": [
                {
                  "title": "完成实验报告",
                  "detail": "利用上午课程后的空档完成核心部分。",
                  "priority": "high",
                  "suggestedDate": "2026-06-08",
                  "startTime": "10:00",
                  "endTime": "11:00",
                  "evidenceRefs": ["bad:1", "bad:2", "bad:3", "bad:4", "task:3"]
                },
                {
                  "title": "虚构建议",
                  "detail": "没有本地依据。",
                  "priority": "normal",
                  "evidenceRefs": ["unknown:9"]
                },
                {
                  "title": "复习课程",
                  "detail": "回顾课堂重点。",
                  "priority": "urgent",
                  "suggestedDate": "2026-06-08",
                  "startTime": "08:30",
                  "endTime": "09:00",
                  "evidenceRefs": ["course:1"]
                },
                {
                  "title": "天气合适时步行",
                  "detail": "出发前仍应查看最新天气。",
                  "priority": "low",
                  "evidenceRefs": ["weather:current"]
                }
              ],
              "warnings": ["课程周次未确认", "课程周次未确认"]
            }
            \`\`\`
        """.trimIndent()

        val result = validator.parseAndValidate(raw, dashboardAdviceSnapshot())
        val advice = requireNotNull(result.advice)

        assertEquals(3, advice.items.size)
        assertEquals(listOf("task:3"), advice.items[0].evidenceRefs)
        assertEquals(AiAdvicePriority.HIGH, advice.items[0].priority)
        assertEquals("10:00", advice.items[0].startTime)
        assertEquals(AiAdvicePriority.NORMAL, advice.items[1].priority)
        assertEquals("2026-06-08", advice.items[1].suggestedDate)
        assertNull(advice.items[1].startTime)
        assertTrue(advice.warnings.any { it.contains("优先级无效") })
        assertTrue(advice.warnings.any { it.contains("已有安排冲突") })
        assertTrue(advice.warnings.any { it.contains("仅保留前三条") })
        assertEquals(1, advice.warnings.count { it == "课程周次未确认" })
    }

    @Test
    fun parseAndValidate_rejectsRealtimeWeatherEvidenceForStaleCache() {
        val raw = """
            {
              "headline": "注意天气",
              "summary": "根据当前天气安排出行。",
              "items": [
                {
                  "title": "现在出门",
                  "detail": "天气适合步行。",
                  "priority": "normal",
                  "evidenceRefs": ["weather:current"]
                }
              ]
            }
        """.trimIndent()

        val result = validator.parseAndValidate(
            raw,
            dashboardAdviceSnapshot(weatherFresh = false)
        )

        assertNull(result.advice)
        assertTrue(result.warnings.any { it.contains("没有可核验") })
    }

    @Test
    fun parseAndValidate_limitsTextAndRejectsMalformedPayload() {
        val longHeadline = "今".repeat(90)
        val longSummary = "学习".repeat(180)
        val valid = """
            {
              "headline": "$longHeadline",
              "summary": "$longSummary",
              "items": [{
                "title": "执行任务",
                "detail": "完成临期任务",
                "priority": "normal",
                "evidenceRefs": ["task:3"]
              }]
            }
        """.trimIndent()

        val advice = requireNotNull(
            validator.parseAndValidate(valid, dashboardAdviceSnapshot()).advice
        )
        assertEquals(60, advice.headline.length)
        assertEquals(240, advice.summary.length)

        val malformed = validator.parseAndValidate("not-json", dashboardAdviceSnapshot())
        assertNull(malformed.advice)
        assertFalse(malformed.warnings.isEmpty())
    }
}
