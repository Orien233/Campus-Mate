package com.example.campusmate.domain.ai.advice

import com.example.campusmate.domain.ai.context.AiContextSnapshot

object DashboardAdviceCachePolicy {
    fun isReusable(
        envelope: AiDashboardAdviceEnvelope,
        snapshot: AiContextSnapshot,
        nowMillis: Long
    ): Boolean {
        return envelope.targetDate == snapshot.rangeStart &&
            envelope.promptTag == LlmDashboardAdvicePromptFactory.PROMPT_TAG &&
            envelope.contextFingerprint == DashboardAdviceContextPolicy.fingerprint(snapshot) &&
            nowMillis >= envelope.generatedAt - MAX_FUTURE_CLOCK_SKEW_MILLIS &&
            nowMillis < envelope.expiresAt
    }

    private const val MAX_FUTURE_CLOCK_SKEW_MILLIS = 5L * 60L * 1000L
}
