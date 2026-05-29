package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmProviderType

object LlmClientFactory {
    fun create(config: LlmProviderConfig): LlmClient {
        return when (config.providerType) {
            LlmProviderType.GEMINI -> GeminiLlmClient()
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderType.CUSTOM_OPENAI_COMPATIBLE -> OpenAiCompatibleLlmClient()
        }
    }
}
