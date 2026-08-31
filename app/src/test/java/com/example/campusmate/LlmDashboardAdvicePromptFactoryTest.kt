package com.example.campusmate

import com.example.campusmate.domain.ai.advice.LlmDashboardAdvicePromptFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmDashboardAdvicePromptFactoryTest {
    @Test
    fun buildRequest_usesVersionedBoundaryAndLocalEvidenceAllowlist() {
        val request = LlmDashboardAdvicePromptFactory.buildRequest(dashboardAdviceSnapshot())

        assertEquals("campusmate.dashboard.advice@v2", request.promptTag)
        assertTrue(request.responseJsonOnly)
        assertTrue(
            request.userPrompt.contains(
                """<UNTRUSTED_DATA label="DASHBOARD_AI_CONTEXT">"""
            )
        )
        assertTrue(request.userPrompt.contains("task:3"))
        assertTrue(request.userPrompt.contains("weather:current"))
        assertTrue(request.userPrompt.contains("schedule:occupied"))
        assertTrue(request.systemPrompt.contains("最多三条"))
        assertTrue(request.systemPrompt.contains("usableForRealtimeAdvice=true"))
        assertTrue(request.systemPrompt.contains("不得宣称已创建、修改、完成或删除"))
        assertFalse(request.systemPrompt.contains("\"url\""))
    }

    @Test
    fun buildRequest_doesNotAllowRealtimeWeatherReferenceWhenCacheIsStale() {
        val request = LlmDashboardAdvicePromptFactory.buildRequest(
            dashboardAdviceSnapshot(weatherFresh = false)
        )

        val instruction = request.userPrompt.substringBefore("<UNTRUSTED_DATA")
        assertFalse(instruction.contains("weather:current"))
    }
}
