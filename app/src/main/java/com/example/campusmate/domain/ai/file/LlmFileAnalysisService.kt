package com.example.campusmate.domain.ai.file

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.domain.llm.LlmClient
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.domain.llm.LlmInlineDataPolicy
import com.example.campusmate.domain.llm.LlmSettingsSource

class LlmFileAnalysisService(
    private val settingsSource: LlmSettingsSource,
    private val clientFactory: (LlmProviderConfig) -> LlmClient = LlmClientFactory::create,
    private val validator: LlmFileAnalysisValidator = LlmFileAnalysisValidator()
) {
    fun unavailableReason(): AiFileAnalysisError? {
        val config = settingsSource.getConfig()
        return when {
            !config.enabled || !config.fileAnalysisEnabled -> AiFileAnalysisError.DISABLED
            !settingsSource.hasApiKey() -> AiFileAnalysisError.NO_API_KEY
            else -> null
        }
    }

    fun generate(
        input: AiFileInput,
        snapshotProvider: () -> AiContextSnapshot
    ): AiFileAnalysisResult {
        val config = settingsSource.getConfig()
        if (!config.enabled || !config.fileAnalysisEnabled) {
            return failure(AiFileAnalysisError.DISABLED, "AI 文件分析未启用")
        }
        val apiKey = settingsSource.getApiKey()
            ?: return failure(AiFileAnalysisError.NO_API_KEY, "请先配置 AI API Key")
        if (!isInputValid(input, config)) {
            return failure(AiFileAnalysisError.INVALID_INPUT, "当前文件或模型能力不符合分析要求")
        }

        return try {
            val snapshot = snapshotProvider()
            if (snapshot.purpose != AiContextPurpose.FILE_ANALYSIS) {
                return failure(AiFileAnalysisError.INVALID_INPUT, "文件分析上下文用途无效")
            }
            val request = LlmFileAnalysisPromptFactory.buildRequest(input, snapshot)
            when (val result = clientFactory(config).generate(request, config, apiKey)) {
                is LlmGenerateResult.Success -> {
                    val validation = validator.parseAndValidate(result.text, snapshot)
                    val analysis = validation.analysis
                        ?: return failure(
                            AiFileAnalysisError.INVALID_RESPONSE,
                            "AI 返回内容无法通过本地校验"
                        )
                    val withContextWarnings = analysis.copy(
                        warnings = (analysis.warnings + snapshot.warnings.map { it.message })
                            .distinct()
                            .take(MAX_WARNINGS)
                    )
                    AiFileAnalysisResult.Success(
                        AiFileAnalysisEnvelope(
                            analysis = withContextWarnings,
                            providerName = result.providerName.trim().take(MAX_PROVENANCE_CHARS)
                                .ifBlank { config.displayName.trim().take(MAX_PROVENANCE_CHARS) },
                            model = result.model.trim().take(MAX_PROVENANCE_CHARS)
                                .ifBlank { config.model.trim().take(MAX_PROVENANCE_CHARS) },
                            promptTag = request.promptTag
                        )
                    )
                }
                is LlmGenerateResult.Failure -> failure(
                    AiFileAnalysisError.REQUEST_FAILED,
                    "AI 文件分析请求失败"
                )
            }
        } catch (_: Exception) {
            failure(AiFileAnalysisError.REQUEST_FAILED, "AI 文件分析请求失败")
        }
    }

    private fun isInputValid(input: AiFileInput, config: LlmProviderConfig): Boolean {
        if (input.fileRef != AI_FILE_SELECTION_REF || input.sizeBytes <= 0L) return false
        return when (input) {
            is AiFileInput.Text -> {
                input.sizeBytes <= AiFileInputPolicy.MAX_TEXT_BYTES &&
                    input.content.isNotBlank() &&
                    input.content.length <= AiFileInputPolicy.MAX_TEXT_CHARS &&
                    AiFileInputPolicy.resolveKind(input.mimeType, "") == AiFileKind.TEXT
            }
            is AiFileInput.Inline -> {
                LlmInlineDataPolicy.validate(
                        listOf(input.data),
                        config.multimodalCapability
                    ) == null
            }
        }
    }

    private fun failure(error: AiFileAnalysisError, message: String): AiFileAnalysisResult.Failure {
        return AiFileAnalysisResult.Failure(error, message)
    }

    companion object {
        private const val MAX_WARNINGS = 16
        private const val MAX_PROVENANCE_CHARS = 80
    }
}
