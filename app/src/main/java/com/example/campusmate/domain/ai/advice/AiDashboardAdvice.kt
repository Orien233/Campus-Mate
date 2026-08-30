package com.example.campusmate.domain.ai.advice

enum class AiAdvicePriority(val wireValue: String) {
    HIGH("high"),
    NORMAL("normal"),
    LOW("low");

    companion object {
        fun fromWireValue(value: String): AiAdvicePriority? {
            return entries.firstOrNull { it.wireValue == value.trim().lowercase() }
        }
    }
}

data class AiDashboardAdviceItem(
    val title: String,
    val detail: String,
    val priority: AiAdvicePriority,
    val suggestedDate: String?,
    val startTime: String?,
    val endTime: String?,
    val evidenceRefs: List<String>
)

data class AiDashboardAdvice(
    val headline: String,
    val summary: String,
    val items: List<AiDashboardAdviceItem>,
    val warnings: List<String> = emptyList()
)

data class AiDashboardAdviceEnvelope(
    val advice: AiDashboardAdvice,
    val targetDate: String,
    val generatedAt: Long,
    val expiresAt: Long,
    val promptTag: String,
    val providerName: String,
    val model: String,
    val contextFingerprint: String
)

enum class AiDashboardAdviceUnavailableReason {
    AI_DISABLED,
    DASHBOARD_ADVICE_DISABLED,
    API_KEY_MISSING
}

enum class AiDashboardAdviceFailureReason {
    REQUEST_FAILED,
    INVALID_RESPONSE,
    CONTEXT_CHANGED
}

sealed class AiDashboardAdviceResult {
    data class Success(
        val envelope: AiDashboardAdviceEnvelope
    ) : AiDashboardAdviceResult()

    data class Unavailable(
        val reason: AiDashboardAdviceUnavailableReason
    ) : AiDashboardAdviceResult()

    data class Failure(
        val reason: AiDashboardAdviceFailureReason,
        val recoverable: Boolean
    ) : AiDashboardAdviceResult()
}
