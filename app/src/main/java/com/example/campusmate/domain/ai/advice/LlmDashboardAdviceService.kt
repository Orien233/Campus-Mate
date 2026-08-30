package com.example.campusmate.domain.ai.advice

import com.example.campusmate.data.model.llm.LlmProviderConfig
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.domain.llm.LlmClient
import com.example.campusmate.domain.llm.LlmClientFactory
import com.example.campusmate.domain.llm.LlmGenerateResult
import com.example.campusmate.domain.llm.LlmSettingsSource
import com.example.campusmate.util.DateTimeUtils

class LlmDashboardAdviceService(
    private val settingsSource: LlmSettingsSource,
    private val clientFactory: (LlmProviderConfig) -> LlmClient = LlmClientFactory::create,
    private val validator: LlmDashboardAdviceValidator = LlmDashboardAdviceValidator(),
    private val nowMillisProvider: () -> Long = DateTimeUtils::nowMillis
) {
    fun unavailableReason(): AiDashboardAdviceUnavailableReason? {
        val config = settingsSource.getConfig()
        return when {
            !config.enabled -> AiDashboardAdviceUnavailableReason.AI_DISABLED
            !config.dashboardAdviceEnabled -> AiDashboardAdviceUnavailableReason.DASHBOARD_ADVICE_DISABLED
            !settingsSource.hasApiKey() -> AiDashboardAdviceUnavailableReason.API_KEY_MISSING
            else -> null
        }
    }

    fun generate(
        snapshotProvider: () -> AiContextSnapshot
    ): AiDashboardAdviceResult {
        val config = settingsSource.getConfig()
        if (!config.enabled) {
            return AiDashboardAdviceResult.Unavailable(AiDashboardAdviceUnavailableReason.AI_DISABLED)
        }
        if (!config.dashboardAdviceEnabled) {
            return AiDashboardAdviceResult.Unavailable(
                AiDashboardAdviceUnavailableReason.DASHBOARD_ADVICE_DISABLED
            )
        }
        val apiKey = settingsSource.getApiKey()
            ?: return AiDashboardAdviceResult.Unavailable(
                AiDashboardAdviceUnavailableReason.API_KEY_MISSING
            )

        return try {
            val snapshot = snapshotProvider()
            val request = LlmDashboardAdvicePromptFactory.buildRequest(snapshot)
            when (val llmResult = clientFactory(config).generate(request, config, apiKey)) {
                is LlmGenerateResult.Success -> {
                    val validation = validator.parseAndValidate(llmResult.text, snapshot)
                    val validatedAdvice = validation.advice
                        ?: return AiDashboardAdviceResult.Failure(
                            reason = AiDashboardAdviceFailureReason.INVALID_RESPONSE,
                            recoverable = true
                        )
                    val generatedAt = nowMillisProvider()
                    val advice = validatedAdvice.copy(
                        warnings = (
                            validatedAdvice.warnings +
                                snapshot.warnings.map { it.message }
                            )
                            .distinct()
                            .take(MAX_DISPLAY_WARNINGS)
                    )
                    AiDashboardAdviceResult.Success(
                        AiDashboardAdviceEnvelope(
                            advice = advice,
                            targetDate = snapshot.rangeStart,
                            generatedAt = generatedAt,
                            expiresAt = generatedAt + CACHE_TTL_MILLIS,
                            promptTag = request.promptTag,
                            providerName = llmResult.providerName.trim().take(MAX_PROVENANCE_CHARS)
                                .ifBlank { config.displayName.trim().take(MAX_PROVENANCE_CHARS) },
                            model = llmResult.model.trim().take(MAX_PROVENANCE_CHARS)
                                .ifBlank { config.model.trim().take(MAX_PROVENANCE_CHARS) },
                            contextFingerprint = DashboardAdviceContextPolicy.fingerprint(snapshot)
                        )
                    )
                }

                is LlmGenerateResult.Failure -> {
                    AiDashboardAdviceResult.Failure(
                        reason = AiDashboardAdviceFailureReason.REQUEST_FAILED,
                        recoverable = llmResult.recoverable
                    )
                }
            }
        } catch (_: Exception) {
            AiDashboardAdviceResult.Failure(
                reason = AiDashboardAdviceFailureReason.REQUEST_FAILED,
                recoverable = true
            )
        }
    }

    companion object {
        const val CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L
        private const val MAX_DISPLAY_WARNINGS = 6
        private const val MAX_PROVENANCE_CHARS = 80
    }
}
