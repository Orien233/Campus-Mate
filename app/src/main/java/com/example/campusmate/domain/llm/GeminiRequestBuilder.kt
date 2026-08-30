package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

object GeminiRequestBuilder {
    fun build(request: LlmGenerateRequest, config: LlmProviderConfig): LlmHttpRequest {
        return LlmHttpRequest(
            url = buildUrl(config.baseUrl, config.model),
            body = buildBody(request, config)
        )
    }

    fun buildUrl(baseUrl: String, model: String): String {
        return baseUrl.trim().trimEnd('/') + "/models/${model.trim()}:generateContent"
    }

    fun buildBody(request: LlmGenerateRequest, config: LlmProviderConfig): String {
        LlmInlineDataPolicy.requireValid(request.inlineData, config.multimodalCapability)
        val generationConfig = JSONObject()
            .put("temperature", config.temperature.toDouble())
            .put("maxOutputTokens", config.maxOutputTokens)
        if (request.responseJsonOnly) {
            generationConfig.put("responseMimeType", "application/json")
        }

        val userParts = JSONArray().put(JSONObject().put("text", request.userPrompt))
        request.inlineData.forEach { item ->
            userParts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", item.mimeType.trim().lowercase())
                        .put("data", Base64.getEncoder().encodeToString(item.bytes))
                )
            )
        }

        return JSONObject()
            .put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", LlmJsonPrompt.buildSystemPrompt(request)))
                )
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        userParts
                    )
                )
            )
            .put("generationConfig", generationConfig)
            .toString()
    }
}
