package com.example.campusmate.domain.plan

import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateRequest

class LlmPlanGenerateService(
    private val llmSettingsRepository: LlmSettingsRepository,
    private val llmClientFactory: LlmClientFactory = LlmClientFactory
) {
    fun isAvailable(): Boolean {
        val config = llmSettingsRepository.getConfig()
        return config.enabled && config.planGenerateEnabled && llmSettingsRepository.hasApiKey()
    }

    fun buildPrompt(context: StudyPlanContext, maxTasks: Int = 12): LlmGenerateRequest {
        return LlmPlanPromptFactory.buildRequest(context, maxTasks)
    }

    @Suppress("unused")
    fun createClientForCurrentConfig() = llmClientFactory.create(llmSettingsRepository.getConfig())
}
