package com.example.campusmate.data.model.llm

data class LlmProviderConfig(
    val enabled: Boolean = false,
    val scheduleParseEnabled: Boolean = true,
    val taskParseEnabled: Boolean = true,
    val planGenerateEnabled: Boolean = true,
    val scheduleParseMode: LlmScheduleParseMode = LlmScheduleParseMode.LLM_FIRST_FALLBACK_LOCAL,
    val providerPresetId: String = "deepseek",
    val providerType: LlmProviderType = LlmProviderType.OPENAI_COMPATIBLE,
    val displayName: String = "DeepSeek",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val authHeaderType: LlmAuthHeaderType = LlmAuthHeaderType.BEARER_AUTHORIZATION,
    val temperature: Float = 0.2f,
    val timeoutMillis: Int = 30_000,
    val maxOutputTokens: Int = 2048
)

enum class LlmScheduleParseMode {
    LLM_FIRST_FALLBACK_LOCAL,
    LOCAL_ONLY
}
