package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig
import org.json.JSONArray
import org.json.JSONObject

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
        val generationConfig = JSONObject()
            .put("temperature", config.temperature.toDouble())
            .put("maxOutputTokens", config.maxOutputTokens)
        if (request.responseJsonOnly) {
            generationConfig.put("responseMimeType", "application/json")
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
                        JSONArray().put(JSONObject().put("text", request.userPrompt))
                    )
                )
            )
            .put("generationConfig", generationConfig)
            .toString()
    }
}
