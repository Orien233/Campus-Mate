package com.example.campusmate.domain.import_

import com.example.campusmate.data.model.llm.LlmScheduleParseMode
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateRequest

class LlmScheduleParseService(
    private val llmSettingsRepository: LlmSettingsRepository,
    private val llmClientFactory: LlmClientFactory = LlmClientFactory
) {
    fun isAvailable(): Boolean {
        val config = llmSettingsRepository.getConfig()
        return config.enabled &&
            config.scheduleParseEnabled &&
            config.scheduleParseMode == LlmScheduleParseMode.LLM_FIRST_FALLBACK_LOCAL &&
            llmSettingsRepository.hasApiKey()
    }

    fun buildPrompt(html: String): LlmGenerateRequest {
        return LlmGenerateRequest(
            systemPrompt = """
                你是 CampusMate 的课表 HTML 结构化助手。
                从用户提供的课表 HTML 中提取课程名称、教师、教室、星期、节次、周次和单双周。
                当前阶段只构造请求，不直接写入数据库。
            """.trimIndent(),
            userPrompt = html.trim(),
            responseJsonOnly = true
        )
    }

    @Suppress("unused")
    fun createClientForCurrentConfig() = llmClientFactory.create(llmSettingsRepository.getConfig())
}
