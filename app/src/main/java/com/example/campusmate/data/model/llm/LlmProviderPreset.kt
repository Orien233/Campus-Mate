package com.example.campusmate.data.model.llm

data class LlmProviderPreset(
    val id: String,
    val displayName: String,
    val providerType: LlmProviderType,
    val baseUrl: String,
    val defaultModel: String,
    val authHeaderType: LlmAuthHeaderType,
    val notes: String
)

enum class LlmAuthHeaderType {
    BEARER_AUTHORIZATION,
    X_GOOG_API_KEY
}
