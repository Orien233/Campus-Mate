package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmInlineData
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
        assertEquals("user", json.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertFalse(body.contains("test-api-key"))
    }

    @Test
    fun buildBodyEncodesImageAsUserContentPart() {
        val body = OpenAiCompatibleRequestBuilder.buildBody(
            request = LlmGenerateRequest(
                systemPrompt = "system",
                userPrompt = "analyze",
                inlineData = listOf(LlmInlineData("image/png", byteArrayOf(1, 2, 3)))
            ),
            config = LlmProviderConfig(
                model = "vision-model",
                multimodalCapability = LlmMultimodalCapability.IMAGE_INPUT
            )
        )

        val content = JSONObject(body)
            .getJSONArray("messages")
            .getJSONObject(1)
            .getJSONArray("content")
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertEquals(
            "data:image/png;base64,AQID",
            content.getJSONObject(1).getJSONObject("image_url").getString("url")
        )
    }

    @Test
    fun buildBodyEncodesPdfAsGenericFilePart() {
        val body = OpenAiCompatibleRequestBuilder.buildBody(
            request = LlmGenerateRequest(
                systemPrompt = "system",
                userPrompt = "analyze",
                inlineData = listOf(LlmInlineData("application/pdf", byteArrayOf(1, 2, 3)))
            ),
            config = LlmProviderConfig(
                multimodalCapability = LlmMultimodalCapability.IMAGE_AND_FILE_INPUT
            )
        )

        val file = JSONObject(body)
            .getJSONArray("messages")
            .getJSONObject(1)
            .getJSONArray("content")
            .getJSONObject(1)
            .getJSONObject("file")
        assertEquals("campusmate-upload.pdf", file.getString("filename"))
        assertEquals("AQID", file.getString("file_data"))
        assertFalse(body.contains("C:\\"))
    }
}
