package com.example.campusmate.domain.ai.command

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.domain.llm.LlmClient
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.domain.llm.LlmSettingsSource

class AiRecordCommandService(
    private val settingsSource: LlmSettingsSource,
    private val clientFactory: (LlmProviderConfig) -> LlmClient = LlmClientFactory::create,
    private val validator: AiRecordCommandValidator = AiRecordCommandValidator()
) {
    fun generate(input: String, contextProvider: () -> AiRecordCommandContext): AiRecordCommandResult {
        val normalized = input.trim()
        if (normalized.isBlank() || normalized.length > AiRecordCommandPromptFactory.MAX_INPUT_CHARS) {
            return failure(AiRecordCommandError.INVALID_INPUT, "请输入 1-4000 字的整理内容")
        }
        val config = settingsSource.getConfig()
        if (!config.enabled || !config.fileAnalysisEnabled) {
            return failure(AiRecordCommandError.DISABLED, "AI 内容整理未启用")
        }
        val apiKey = settingsSource.getApiKey()
            ?: return failure(AiRecordCommandError.NO_API_KEY, "请先配置 AI API Key")
        return try {
            val context = contextProvider()
            val request = AiRecordCommandPromptFactory.buildRequest(normalized, context)
            when (val result = clientFactory(config).generate(request, config, apiKey)) {
                is LlmGenerateResult.Success -> {
                    val validation = validator.parseAndValidate(result.text, normalized, context)
                    if (validation.changes.isEmpty()) {
                        failure(
                            AiRecordCommandError.INVALID_RESPONSE,
                            validation.warnings.firstOrNull() ?: "没有识别到可预览的有效变更"
                        )
                    } else {
                        AiRecordCommandResult.Success(
                            AiRecordCommandEnvelope(
                                changes = validation.changes,
                                warnings = (validation.warnings + context.warnings).distinct().take(20),
                                providerName = result.providerName.trim().take(80).ifBlank { config.displayName },
                                model = result.model.trim().take(80).ifBlank { config.model },
                                promptTag = request.promptTag
                            )
                        )
                    }
                }
                is LlmGenerateResult.Failure ->
                    failure(AiRecordCommandError.REQUEST_FAILED, "AI 内容整理请求失败")
            }
        } catch (_: Exception) {
            failure(AiRecordCommandError.REQUEST_FAILED, "AI 内容整理请求失败")
        }
    }

    private fun failure(error: AiRecordCommandError, message: String) =
        AiRecordCommandResult.Failure(error, message)
}
