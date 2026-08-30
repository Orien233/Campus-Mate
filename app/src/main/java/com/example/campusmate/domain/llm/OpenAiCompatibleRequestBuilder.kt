package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

object OpenAiCompatibleRequestBuilder {
    fun build(request: LlmGenerateRequest, config: LlmProviderConfig): LlmHttpRequest {
        return LlmHttpRequest(
            url = buildUrl(config.baseUrl),
            body = buildBody(request, config)
        )
    }

    fun buildUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/') + "/chat/completions"
    }

    fun buildBody(request: LlmGenerateRequest, config: LlmProviderConfig): String {
        LlmInlineDataPolicy.requireValid(request.inlineData, config.multimodalCapability)
        val userContent: Any = if (request.inlineData.isEmpty()) {
            request.userPrompt
        } else {
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", request.userPrompt)
                )
                .apply {
                    request.inlineData.forEach { item ->
                        put(item.toOpenAiContentPart())
                    }
                }
        }
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", LlmJsonPrompt.buildSystemPrompt(request))
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", userContent)
            )

        return JSONObject()
            .put("model", config.model.trim())
            .put("messages", messages)
            .put("temperature", config.temperature.toDouble())
            .put("max_tokens", config.maxOutputTokens)
            .put("stream", false)
            .toString()
    }

    private fun LlmInlineData.toOpenAiContentPart(): JSONObject {
        val normalizedMimeType = mimeType.trim().lowercase()
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return if (normalizedMimeType.startsWith("image/")) {
            JSONObject()
                .put("type", "image_url")
                .put(
                    "image_url",
                    JSONObject()
                        .put("url", "data:$normalizedMimeType;base64,$encoded")
                        .put("detail", "auto")
                )
        } else {
            JSONObject()
                .put("type", "file")
                .put(
                    "file",
                    JSONObject()
                        .put("filename", "campusmate-upload.pdf")
                        .put("file_data", encoded)
                )
        }
    }
}
