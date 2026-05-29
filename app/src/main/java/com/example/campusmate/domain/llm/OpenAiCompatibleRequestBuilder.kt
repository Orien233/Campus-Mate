package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig
import org.json.JSONArray
import org.json.JSONObject

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
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", LlmJsonPrompt.buildSystemPrompt(request))
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", request.userPrompt)
            )

        return JSONObject()
            .put("model", config.model.trim())
            .put("messages", messages)
            .put("temperature", config.temperature.toDouble())
            .put("max_tokens", config.maxOutputTokens)
            .put("stream", false)
            .toString()
    }
}
