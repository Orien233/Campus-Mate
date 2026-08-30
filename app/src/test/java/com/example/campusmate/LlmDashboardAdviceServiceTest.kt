package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceFailureReason
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceResult
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceUnavailableReason
import com.example.campusmate.domain.ai.advice.LlmDashboardAdviceService
import com.example.campusmate.domain.llm.LlmClient
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.domain.llm.LlmSettingsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmDashboardAdviceServiceTest {
    @Test
    fun generate_doesNotReadContextOrCreateClientWhenUnavailable() {
        val scenarios = listOf(
            FakeSettings(
                storedConfig = LlmProviderConfig(enabled = false, dashboardAdviceEnabled = true),
                key = "key"
            ) to AiDashboardAdviceUnavailableReason.AI_DISABLED,
            FakeSettings(
                storedConfig = LlmProviderConfig(enabled = true, dashboardAdviceEnabled = false),
                key = "key"
            ) to AiDashboardAdviceUnavailableReason.DASHBOARD_ADVICE_DISABLED,
            FakeSettings(
                storedConfig = LlmProviderConfig(enabled = true, dashboardAdviceEnabled = true),
                key = null
            ) to AiDashboardAdviceUnavailableReason.API_KEY_MISSING
        )

        scenarios.forEach { (settings, expectedReason) ->
            var contextReads = 0
            var clientCreations = 0
            val service = LlmDashboardAdviceService(
                settingsSource = settings,
                clientFactory = {
                    clientCreations += 1
                    FakeClient(LlmGenerateResult.Failure("unused"))
                }
            )

            val result = service.generate {
                contextReads += 1
                dashboardAdviceSnapshot()
            }

            assertEquals(expectedReason, (result as AiDashboardAdviceResult.Unavailable).reason)
            assertEquals(0, contextReads)
            assertEquals(0, clientCreations)
        }
    }

    @Test
    fun generate_validatesResponseAndBuildsSafeEnvelope() {
        val now = 1_781_000_000_000L
        val client = FakeClient(
            LlmGenerateResult.Success(
                text = VALID_RESPONSE,
                rawJson = """{"secret":"raw response must not be cached"}""",
                providerName = "Test Provider",
                model = "test-model"
            )
        )
        val service = LlmDashboardAdviceService(
            settingsSource = FakeSettings(
                LlmProviderConfig(enabled = true, dashboardAdviceEnabled = true),
                "private-api-key"
            ),
            clientFactory = { client },
            nowMillisProvider = { now }
        )
        var contextReads = 0

        val result = service.generate {
            contextReads += 1
            dashboardAdviceSnapshot()
        } as AiDashboardAdviceResult.Success

        assertEquals(1, contextReads)
        assertEquals(1, client.generateCalls)
        assertEquals("private-api-key", client.receivedApiKey)
        assertEquals("campusmate.dashboard.advice@v1", client.receivedRequest?.promptTag)
        assertEquals("2026-06-08", result.envelope.targetDate)
        assertEquals(now, result.envelope.generatedAt)
        assertEquals(now + LlmDashboardAdviceService.CACHE_TTL_MILLIS, result.envelope.expiresAt)
        assertEquals(64, result.envelope.contextFingerprint.length)
        assertEquals("Test Provider", result.envelope.providerName)
        assertTrue(result.envelope.advice.warnings.contains("课程教学周尚未解析"))
    }

    @Test
    fun generate_mapsClientAndValidationFailuresWithoutEnvelope() {
        val settings = FakeSettings(
            LlmProviderConfig(enabled = true, dashboardAdviceEnabled = true),
            "key"
        )
        val requestFailure = LlmDashboardAdviceService(
            settingsSource = settings,
            clientFactory = {
                FakeClient(LlmGenerateResult.Failure("HTTP 503", recoverable = false))
            }
        ).generate { dashboardAdviceSnapshot() } as AiDashboardAdviceResult.Failure
        assertEquals(AiDashboardAdviceFailureReason.REQUEST_FAILED, requestFailure.reason)
        assertEquals(false, requestFailure.recoverable)

        val invalidResponse = LlmDashboardAdviceService(
            settingsSource = settings,
            clientFactory = {
                FakeClient(
                    LlmGenerateResult.Success(
                        text = "{}",
                        providerName = "Provider",
                        model = "model"
                    )
                )
            }
        ).generate { dashboardAdviceSnapshot() } as AiDashboardAdviceResult.Failure
        assertEquals(AiDashboardAdviceFailureReason.INVALID_RESPONSE, invalidResponse.reason)
        assertTrue(invalidResponse.recoverable)
    }

    private class FakeSettings(
        private val storedConfig: LlmProviderConfig,
        val key: String?
    ) : LlmSettingsSource {
        override fun getConfig(): LlmProviderConfig = storedConfig
        override fun hasApiKey(): Boolean = key != null
        override fun getApiKey(): String? = key
    }

    private class FakeClient(
        private val result: LlmGenerateResult
    ) : LlmClient {
        var generateCalls: Int = 0
        var receivedRequest: LlmGenerateRequest? = null
        var receivedApiKey: String? = null

        override fun generate(
            request: LlmGenerateRequest,
            config: LlmProviderConfig,
            apiKey: String
        ): LlmGenerateResult {
            generateCalls += 1
            receivedRequest = request
            receivedApiKey = apiKey
            return result
        }

        override fun testConnection(
            config: LlmProviderConfig,
            apiKey: String
        ): LlmGenerateResult = result
    }

    companion object {
        private val VALID_RESPONSE = """
            {
              "headline": "先完成临期任务",
              "summary": "课程结束后优先完成实验报告。",
              "items": [{
                "title": "完成实验报告",
                "detail": "先完成核心内容，再检查格式。",
                "priority": "high",
                "suggestedDate": "2026-06-08",
                "startTime": "10:00",
                "endTime": "11:00",
                "evidenceRefs": ["task:3"]
              }],
              "warnings": []
            }
        """.trimIndent()
    }
}
