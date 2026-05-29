package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.domain.llm.GeminiRequestBuilder
import com.example.campusmate.domain.llm.LlmGenerateRequest
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
        assertFalse(body.contains("test-api-key"))
    }
}
