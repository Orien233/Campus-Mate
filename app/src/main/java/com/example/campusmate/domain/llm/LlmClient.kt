package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmProviderConfig

interface LlmClient {
    fun generate(
        request: LlmGenerateRequest,
        config: LlmProviderConfig,
        apiKey: String
    ): LlmGenerateResult

    fun testConnection(
        config: LlmProviderConfig,
        apiKey: String
    ): LlmGenerateResult
}
