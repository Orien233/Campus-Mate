package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.file.AiFileAnalysisError
import com.example.campusmate.domain.ai.file.AiFileAnalysisResult
import com.example.campusmate.domain.ai.file.AiFileInput
import com.example.campusmate.domain.ai.file.LlmFileAnalysisService
import com.example.campusmate.domain.llm.LlmClient
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.domain.llm.LlmInlineData
import com.example.campusmate.domain.llm.LlmSettingsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmFileAnalysisServiceTest {
    @Test
    fun generate_doesNotReadContextOrCreateClientWhenUnavailable() {
        val scenarios = listOf(
            FakeSettings(LlmProviderConfig(enabled = false), "key") to AiFileAnalysisError.DISABLED,
            FakeSettings(
                LlmProviderConfig(enabled = true, fileAnalysisEnabled = false),
                "key"
            ) to AiFileAnalysisError.DISABLED,
            FakeSettings(
                LlmProviderConfig(enabled = true, fileAnalysisEnabled = true),
                null
            ) to AiFileAnalysisError.NO_API_KEY
        )

        scenarios.forEach { (settings, expectedError) ->
            var contextReads = 0
            var clientCreations = 0
            val service = LlmFileAnalysisService(
                settingsSource = settings,
                clientFactory = {
                    clientCreations += 1
                    FakeClient(LlmGenerateResult.Failure("unused"))
                }
            )

            val result = service.generate(TEXT_INPUT) {
                contextReads += 1
                fileSnapshot()
            } as AiFileAnalysisResult.Failure

            assertEquals(expectedError, result.error)
            assertEquals(0, contextReads)
            assertEquals(0, clientCreations)
        }
    }

    @Test
    fun generate_validatesResponseAndBuildsMetadataOnlyEnvelope() {
        val client = FakeClient(
            LlmGenerateResult.Success(
                text = VALID_RESPONSE,
                rawJson = """{"raw":"must not be retained"}""",
                providerName = "Test Provider",
                model = "vision-test"
            )
        )
        val service = LlmFileAnalysisService(
            settingsSource = FakeSettings(
                LlmProviderConfig(enabled = true, fileAnalysisEnabled = true),
                "private-key"
            ),
            clientFactory = { client }
        )

        val result = service.generate(TEXT_INPUT) { fileSnapshot() } as AiFileAnalysisResult.Success

        assertEquals(1, client.generateCalls)
        assertEquals("private-key", client.receivedApiKey)
        assertEquals("campusmate.file.analysis@v1", client.receivedRequest?.promptTag)
        assertTrue(client.receivedRequest?.inlineData?.isEmpty() == true)
        assertEquals("Test Provider", result.envelope.providerName)
        assertEquals("vision-test", result.envelope.model)
        assertEquals("读取到可供用户确认的学习信息。", result.envelope.analysis.summary)
        assertTrue(result.envelope.analysis.warnings.contains("课程教学周尚未解析"))
        assertFalse(result.envelope.toString().contains("private-key"))
        assertFalse(result.envelope.toString().contains("must not be retained"))
    }

    @Test
    fun generate_rejectsInlineInputBeforeContextWhenCapabilityIsTextOnly() {
        var contextReads = 0
        var clientCreations = 0
        val service = LlmFileAnalysisService(
            settingsSource = FakeSettings(
                LlmProviderConfig(
                    enabled = true,
                    fileAnalysisEnabled = true,
                    multimodalCapability = LlmMultimodalCapability.TEXT_ONLY
                ),
                "key"
            ),
            clientFactory = {
                clientCreations += 1
                FakeClient(LlmGenerateResult.Failure("unused"))
            }
        )

        val result = service.generate(
            AiFileInput.Inline(LlmInlineData("image/png", byteArrayOf(1, 2, 3)))
        ) {
            contextReads += 1
            fileSnapshot()
        } as AiFileAnalysisResult.Failure

        assertEquals(AiFileAnalysisError.INVALID_INPUT, result.error)
        assertEquals(0, contextReads)
        assertEquals(0, clientCreations)
    }

    @Test
    fun generate_passesInlineInputOnlyForDeclaredCapability() {
        val client = FakeClient(
            LlmGenerateResult.Success(
                text = VALID_RESPONSE,
                providerName = "Provider",
                model = "model"
            )
        )
        val service = LlmFileAnalysisService(
            settingsSource = FakeSettings(
                LlmProviderConfig(
                    enabled = true,
                    fileAnalysisEnabled = true,
                    multimodalCapability = LlmMultimodalCapability.IMAGE_INPUT
                ),
                "key"
            ),
            clientFactory = { client }
        )

        val result = service.generate(
            AiFileInput.Inline(LlmInlineData("image/png", byteArrayOf(1, 2, 3))),
            { fileSnapshot() }
        )

        assertTrue(result is AiFileAnalysisResult.Success)
        assertEquals(1, client.receivedRequest?.inlineData?.size)
    }

    @Test
    fun generate_mapsClientAndValidationFailures() {
        val settings = FakeSettings(
            LlmProviderConfig(enabled = true, fileAnalysisEnabled = true),
            "key"
        )
        val requestFailure = LlmFileAnalysisService(
            settingsSource = settings,
            clientFactory = { FakeClient(LlmGenerateResult.Failure("HTTP 503")) }
        ).generate(TEXT_INPUT) { fileSnapshot() } as AiFileAnalysisResult.Failure
        assertEquals(AiFileAnalysisError.REQUEST_FAILED, requestFailure.error)

        val invalidResponse = LlmFileAnalysisService(
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
        ).generate(TEXT_INPUT) { fileSnapshot() } as AiFileAnalysisResult.Failure
        assertEquals(AiFileAnalysisError.INVALID_RESPONSE, invalidResponse.error)
    }

    private class FakeSettings(
        private val storedConfig: LlmProviderConfig,
        private val key: String?
    ) : LlmSettingsSource {
        override fun getConfig(): LlmProviderConfig = storedConfig
        override fun hasApiKey(): Boolean = key != null
        override fun getApiKey(): String? = key
    }

    private class FakeClient(
        private val result: LlmGenerateResult
    ) : LlmClient {
        var generateCalls = 0
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
        private val TEXT_INPUT = AiFileInput.Text("text/plain", 12L, "课程与作业安排")
        private val VALID_RESPONSE = """
            {
              "schemaVersion": 1,
              "summary": "读取到可供用户确认的学习信息。",
              "keyPoints": [],
              "courses": [],
              "tasks": [],
              "plans": [],
              "insights": [],
              "localMatches": [],
              "warnings": []
            }
        """.trimIndent()

        private fun fileSnapshot() = dashboardAdviceSnapshot()
            .copy(purpose = AiContextPurpose.FILE_ANALYSIS)
    }
}
