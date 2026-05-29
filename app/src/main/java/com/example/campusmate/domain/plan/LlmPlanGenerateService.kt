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

    fun buildPrompt(input: String): LlmGenerateRequest {
        return LlmGenerateRequest(
            systemPrompt = """
                你是 CampusMate 的学习计划生成助手。
                根据课程、任务和可用时间生成结构化学习计划。
                当前阶段只构造请求，不直接写入数据库。
            """.trimIndent(),
            userPrompt = input.trim(),
            responseJsonOnly = true
        )
    }

    @Suppress("unused")
    fun createClientForCurrentConfig() = llmClientFactory.create(llmSettingsRepository.getConfig())
}
