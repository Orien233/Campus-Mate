package com.example.campusmate.domain.task

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.llm.LlmClient
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.util.DateTimeUtils

class LlmTaskParseService(
    private val llmSettingsRepository: LlmSettingsRepository,
    private val llmClientFactory: (LlmProviderConfig) -> LlmClient = { config -> LlmClientFactory.create(config) },
    private val validator: LlmTaskDraftValidator = LlmTaskDraftValidator()
) {
    fun isAvailable(): Boolean {
        val config = llmSettingsRepository.getConfig()
        return config.enabled && config.taskParseEnabled && llmSettingsRepository.hasApiKey()
    }

    fun buildPrompt(pageContent: String): LlmGenerateRequest {
        return LlmTaskPromptFactory.buildRequest(
            pageContent = pageContent,
            nowText = DateTimeUtils.formatDateTime(DateTimeUtils.nowMillis())
        )
    }

    fun parseWithLlm(pageContent: String): LlmTaskDraftValidationResult {
        val config = llmSettingsRepository.getConfig()
        val apiKey = llmSettingsRepository.getApiKey().orEmpty()
        if (!isAvailable() || apiKey.isBlank()) {
            throw TaskParseException("AI 当前不可用，请先在设置中启用 AI 并填写 API Key。")
        }

        val client = llmClientFactory(config)
        return when (val response = client.generate(buildPrompt(pageContent), config, apiKey)) {
            is LlmGenerateResult.Success -> validator.parse(response.text)
            is LlmGenerateResult.Failure -> throw TaskParseException(response.message)
        }
    }
}
