package com.example.campusmate.domain.import_

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.data.model.llm.LlmScheduleParseMode
import com.example.campusmate.data.repository.LlmSettingsRepository
import com.example.campusmate.domain.llm.LlmClient
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateRequest
import com.example.campusmate.domain.llm.LlmGenerateResult

class LlmScheduleParseService(
    private val settingsSource: LlmScheduleParseSettingsSource,
    private val llmClientFactory: (LlmProviderConfig) -> LlmClient = { config -> LlmClientFactory.create(config) },
    private val localParser: ScheduleParser = JsoupScheduleParser(),
    private val validator: LlmCourseDraftValidator = LlmCourseDraftValidator()
) {
    constructor(
        llmSettingsRepository: LlmSettingsRepository,
        llmClientFactory: (LlmProviderConfig) -> LlmClient = { config -> LlmClientFactory.create(config) },
        localParser: ScheduleParser = JsoupScheduleParser(),
        validator: LlmCourseDraftValidator = LlmCourseDraftValidator()
    ) : this(
        settingsSource = llmSettingsRepository,
        llmClientFactory = llmClientFactory,
        localParser = localParser,
        validator = validator
    )

    fun isAvailable(): Boolean {
        val config = settingsSource.getConfig()
        return config.enabled &&
            config.scheduleParseEnabled &&
            config.scheduleParseMode == LlmScheduleParseMode.LLM_FIRST_FALLBACK_LOCAL &&
            settingsSource.hasApiKey()
    }

    fun buildPrompt(html: String): LlmGenerateRequest {
        return LlmSchedulePromptFactory.buildRequest(html)
    }

    fun parseWithLlm(html: String): ScheduleParseResult {
        val config = settingsSource.getConfig()
        val apiKey = settingsSource.getApiKey().orEmpty()
        if (!isAvailable() || apiKey.isBlank()) {
            throw ScheduleParseException("LLM is currently unavailable. Please use local parsing.")
        }

        val client = llmClientFactory(config)
        val sectionTimeSlots = SectionTimeSlotExtractor.extract(html)
        return when (val response = client.generate(buildPrompt(html), config, apiKey)) {
            is LlmGenerateResult.Success -> {
                val validation = validator.parse(response.text)
                ScheduleParseResult(
                    drafts = validation.drafts,
                    warnings = validation.warnings,
                    parserLabel = "AI-assisted parsing",
                    usedLlm = true,
                    sectionTimeSlots = sectionTimeSlots
                )
            }
            is LlmGenerateResult.Failure -> throw ScheduleParseException(response.message)
        }
    }

    fun parseLocal(html: String, fallbackReason: String? = null): ScheduleParseResult {
        val drafts = localParser.parse(html)
        return ScheduleParseResult(
            drafts = drafts,
            warnings = emptyList(),
            parserLabel = if (fallbackReason.isNullOrBlank()) "Local rules parsing" else "Local rules parsing (LLM fallback)",
            usedLlm = false,
            fallbackReason = fallbackReason,
            sectionTimeSlots = SectionTimeSlotExtractor.extract(html)
        )
    }
}
