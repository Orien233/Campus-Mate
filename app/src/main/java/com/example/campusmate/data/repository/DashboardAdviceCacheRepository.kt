package com.example.campusmate.data.repository

import android.content.Context
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceEnvelope
import com.example.campusmate.domain.ai.advice.DashboardAdviceCachePolicy
import com.example.campusmate.domain.ai.advice.LlmDashboardAdviceCacheCodec
import com.example.campusmate.domain.ai.context.AiContextSnapshot
import com.example.campusmate.util.DateTimeUtils

/** Stores only validated advice; prompts, raw responses, context, and API keys are never cached here. */
class DashboardAdviceCacheRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun loadForSnapshot(
        snapshot: AiContextSnapshot,
        nowMillis: Long = DateTimeUtils.nowMillis()
    ): AiDashboardAdviceEnvelope? {
        val raw = preferences.getString(KEY_LATEST_ADVICE, null) ?: return null
        val envelope = LlmDashboardAdviceCacheCodec.decode(raw) ?: return null
        return envelope.takeIf { DashboardAdviceCachePolicy.isReusable(it, snapshot, nowMillis) }
    }

    fun save(envelope: AiDashboardAdviceEnvelope) {
        preferences.edit()
            .putString(KEY_LATEST_ADVICE, LlmDashboardAdviceCacheCodec.encode(envelope))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_LATEST_ADVICE).apply()
    }

    companion object {
        private const val PREFS_NAME = "campusmate_dashboard_ai_advice"
        private const val KEY_LATEST_ADVICE = "latest_validated_advice"
    }
}
