package com.example.campusmate.domain.ai.advice

import com.example.campusmate.domain.ai.context.AiContextSnapshot

object DashboardAdviceRefreshPolicy {
    fun revalidate(
        result: AiDashboardAdviceResult,
        currentSnapshot: AiContextSnapshot
    ): AiDashboardAdviceResult {
        if (result !is AiDashboardAdviceResult.Success) return result
        val stillCurrent =
            result.envelope.targetDate == currentSnapshot.rangeStart &&
                result.envelope.contextFingerprint ==
                DashboardAdviceContextPolicy.fingerprint(currentSnapshot)
        return if (stillCurrent) {
            result
        } else {
            AiDashboardAdviceResult.Failure(
                reason = AiDashboardAdviceFailureReason.CONTEXT_CHANGED,
                recoverable = true
            )
        }
    }
}
