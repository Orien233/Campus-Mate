package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.OpenAiCompatibleRequestBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleRequestBuilderTest {
    @Test
    fun buildUrlDoesNotCreateDoubleSlash() {
        val url = OpenAiCompatibleRequestBuilder.buildUrl("https://example.com/v1/")

        assertEquals("https://example.com/v1/chat/completions", url)
    }

    @Test
    fun buildBodyContainsCoreFieldsAndNoApiKey() {
        val body = OpenAiCompatibleRequestBuilder.buildBody(
            request = LlmGenerateRequest(
                systemPrompt = "system",
                userPrompt = "user",
                responseJsonOnly = true
            ),
            config = LlmProviderConfig(model = "test-model", temperature = 0.2f)
        )
        val json = JSONObject(body)

        assertEquals("test-model", json.getString("model"))
        assertEquals(0.2, json.getDouble("temperature"), 0.0001)
        assertFalse(json.getBoolean("stream"))
        assertEquals(2, json.getJSONArray("messages").length())
        assertTrue(json.getJSONArray("messages").getJSONObject(0).getString("content").contains("JSON"))
        assertFalse(body.contains("test-api-key"))
    }
}
