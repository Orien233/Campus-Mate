package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.domain.llm.GeminiRequestBuilder
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmInlineData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRequestBuilderTest {
    @Test
    fun buildUrlContainsGenerateContentEndpoint() {
        val url = GeminiRequestBuilder.buildUrl(
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/",
            model = "gemini-3.5-flash"
        )

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
            url
        )
    }

    @Test
    fun buildBodyContainsCoreFieldsAndNoApiKey() {
        val body = GeminiRequestBuilder.buildBody(
            request = LlmGenerateRequest(
                systemPrompt = "system",
                userPrompt = "user",
                responseJsonOnly = true
            ),
            config = LlmProviderConfig(model = "gemini-3.5-flash", temperature = 0.2f)
        )
        val json = JSONObject(body)

        assertTrue(json.has("contents"))
        assertTrue(json.has("system_instruction"))
        assertEquals("application/json", json.getJSONObject("generationConfig").getString("responseMimeType"))
        assertEquals(0.2, json.getJSONObject("generationConfig").getDouble("temperature"), 0.0001)
        assertEquals(
            1,
            json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").length()
        )
        assertFalse(body.contains("test-api-key"))
    }

    @Test
    fun buildBodyAddsInlineDataAfterTextPart() {
        val body = GeminiRequestBuilder.buildBody(
            request = LlmGenerateRequest(
                systemPrompt = "system",
                userPrompt = "analyze",
                inlineData = listOf(LlmInlineData("application/pdf", byteArrayOf(1, 2, 3)))
            ),
            config = LlmProviderConfig(
                model = "gemini-vision",
                multimodalCapability = LlmMultimodalCapability.IMAGE_AND_FILE_INPUT
            )
        )

        val parts = JSONObject(body)
            .getJSONArray("contents")
            .getJSONObject(0)
            .getJSONArray("parts")
        assertEquals(2, parts.length())
        assertEquals("analyze", parts.getJSONObject(0).getString("text"))
        val inlineData = parts.getJSONObject(1).getJSONObject("inline_data")
        assertEquals("application/pdf", inlineData.getString("mime_type"))
        assertEquals("AQID", inlineData.getString("data"))
    }
}
