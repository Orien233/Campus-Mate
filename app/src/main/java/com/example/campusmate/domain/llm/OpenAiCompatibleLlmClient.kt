package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmAuthHeaderType
import java.net.HttpURLConnection
import java.net.URL

class OpenAiCompatibleLlmClient : LlmClient {
    override fun generate(
        request: LlmGenerateRequest,
        config: LlmProviderConfig,
        apiKey: String
    ): LlmGenerateResult {
        return try {
            val httpRequest = OpenAiCompatibleRequestBuilder.build(request, config)
            val connection = (URL(httpRequest.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = LlmHttpUtils.timeoutMillis(config.timeoutMillis)
                readTimeout = LlmHttpUtils.timeoutMillis(config.timeoutMillis)
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                when (config.authHeaderType) {
                    LlmAuthHeaderType.BEARER_AUTHORIZATION ->
                        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    LlmAuthHeaderType.X_GOOG_API_KEY ->
                        setRequestProperty("x-goog-api-key", apiKey.trim())
                }
            }

            try {
                connection.outputStream.use { output ->
                    output.write(httpRequest.body.toByteArray(Charsets.UTF_8))
                }
                val responseCode = connection.responseCode
                val rawJson = LlmHttpUtils.readResponse(connection)
                if (responseCode !in 200..299) {
                    return LlmHttpUtils.failureForHttpCode(responseCode, rawJson, apiKey)
                }
                val content = parseContent(rawJson)
                if (content.isBlank()) {
                    LlmGenerateResult.Failure(
                        message = "\u6a21\u578b\u8fd4\u56de\u4e3a\u7a7a\uff0c\u8bf7\u68c0\u67e5\u6a21\u578b\u540d\u548c\u8f93\u51fa\u683c\u5f0f",
                        detail = null
                    )
                } else {
                    LlmGenerateResult.Success(
                        text = content,
                        rawJson = rawJson,
                        providerName = config.displayName,
                        model = config.model
                    )
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            LlmHttpUtils.failureForException(error)
        }
    }

    override fun testConnection(
        config: LlmProviderConfig,
        apiKey: String
    ): LlmGenerateResult {
        return generate(
            request = LlmGenerateRequest(
                systemPrompt = "\u4f60\u662f CampusMate \u7684\u8fde\u63a5\u6d4b\u8bd5\u52a9\u624b\u3002",
                userPrompt = "\u8bf7\u8fd4\u56de {\"ok\": true}\u3002",
                responseJsonOnly = true
            ),
            config = config,
            apiKey = apiKey
        )
    }

    private fun parseContent(rawJson: String): String {
        return org.json.JSONObject(rawJson)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
    }
}
