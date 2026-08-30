package com.example.campusmate

import com.example.campusmate.domain.ai.advice.AiAdvicePriority
import com.example.campusmate.domain.ai.advice.AiDashboardAdvice
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceEnvelope
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceFailureReason
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceItem
import com.example.campusmate.domain.ai.advice.AiDashboardAdviceResult
import com.example.campusmate.domain.ai.advice.DashboardAdviceCachePolicy
import com.example.campusmate.domain.ai.advice.DashboardAdviceContextPolicy
import com.example.campusmate.domain.ai.advice.DashboardAdviceRefreshPolicy
import com.example.campusmate.domain.ai.advice.LlmDashboardAdviceCacheCodec
import com.example.campusmate.domain.ai.advice.LlmDashboardAdvicePromptFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmDashboardAdviceCacheCodecTest {
    @Test
    fun encodeDecode_roundTripsValidatedEnvelopeWithoutRequestData() {
        val envelope = envelope()

        val encoded = LlmDashboardAdviceCacheCodec.encode(envelope)
        val decoded = LlmDashboardAdviceCacheCodec.decode(encoded)

        assertEquals(envelope, decoded)
        assertFalse(encoded.contains("api-key", ignoreCase = true))
        assertFalse(encoded.contains("Authorization", ignoreCase = true))
        assertFalse(encoded.contains("rawJson", ignoreCase = true))
        assertFalse(encoded.contains("DASHBOARD_AI_CONTEXT"))
    }

    @Test
    fun decode_rejectsCorruptOrOldPromptCache() {
        assertNull(LlmDashboardAdviceCacheCodec.decode("not-json"))
        val oldPrompt = LlmDashboardAdviceCacheCodec.encode(envelope())
            .replace(
                LlmDashboardAdvicePromptFactory.PROMPT_TAG,
                "campusmate.dashboard.advice@v0"
            )
        assertNull(LlmDashboardAdviceCacheCodec.decode(oldPrompt))
    }

    @Test
    fun fingerprint_ignoresCaptureAgeButChangesWhenFactsChange() {
        val snapshot = dashboardAdviceSnapshot()
        val fingerprint = DashboardAdviceContextPolicy.fingerprint(snapshot)
        val recaptured = snapshot.copy(
            generatedAt = snapshot.generatedAt + 60_000L,
            weather = snapshot.weather?.copy(ageMillis = snapshot.weather.ageMillis + 60_000L)
        )
        val weatherUpdated = snapshot.copy(
            weather = snapshot.weather?.copy(updatedAt = snapshot.weather.updatedAt + 60_000L)
        )
        val taskUpdated = snapshot.copy(
            pendingTasks = snapshot.pendingTasks.map { it.copy(title = it.title + "（更新）") }
        )

        assertEquals(64, fingerprint.length)
        assertEquals(fingerprint, DashboardAdviceContextPolicy.fingerprint(recaptured))
        assertNotEquals(fingerprint, DashboardAdviceContextPolicy.fingerprint(weatherUpdated))
        assertNotEquals(fingerprint, DashboardAdviceContextPolicy.fingerprint(taskUpdated))
    }

    @Test
    fun cachePolicy_rejectsExpiredWeatherChangedFactsAndLateFutureResults() {
        val snapshot = dashboardAdviceSnapshot()
        val generatedAt = snapshot.generatedAt
        val validEnvelope = envelope().copy(
            generatedAt = generatedAt,
            expiresAt = generatedAt + 6 * 60 * 60 * 1000L,
            contextFingerprint = DashboardAdviceContextPolicy.fingerprint(snapshot)
        )

        assertTrue(
            DashboardAdviceCachePolicy.isReusable(
                validEnvelope,
                snapshot,
                generatedAt + 10 * 60 * 1000L
            )
        )
        assertFalse(
            DashboardAdviceCachePolicy.isReusable(
                validEnvelope,
                snapshot,
                validEnvelope.expiresAt
            )
        )

        val staleWeather = snapshot.copy(
            weather = snapshot.weather?.copy(
                ageMillis = 31 * 60 * 1000L,
                freshness = com.example.campusmate.domain.ai.context.AiWeatherFreshness.STALE,
                usableForRealtimeAdvice = false
            )
        )
        assertFalse(
            DashboardAdviceCachePolicy.isReusable(
                validEnvelope,
                staleWeather,
                generatedAt + 31 * 60 * 1000L
            )
        )

        val changedTask = snapshot.copy(
            pendingTasks = snapshot.pendingTasks.map { it.copy(overdue = true) }
        )
        assertFalse(
            DashboardAdviceCachePolicy.isReusable(
                validEnvelope,
                changedTask,
                generatedAt + 10 * 60 * 1000L
            )
        )

        val futureEnvelope = validEnvelope.copy(
            generatedAt = generatedAt + 6 * 60 * 1000L,
            expiresAt = generatedAt + 7 * 60 * 60 * 1000L
        )
        assertFalse(
            DashboardAdviceCachePolicy.isReusable(
                futureEnvelope,
                snapshot,
                generatedAt
            )
        )
    }

    @Test
    fun refreshPolicy_rejectsResponseWhenFactsChangedDuringRequest() {
        val requestSnapshot = dashboardAdviceSnapshot()
        val success = AiDashboardAdviceResult.Success(
            envelope().copy(
                targetDate = requestSnapshot.rangeStart,
                contextFingerprint = DashboardAdviceContextPolicy.fingerprint(requestSnapshot)
            )
        )
        assertEquals(
            success,
            DashboardAdviceRefreshPolicy.revalidate(success, requestSnapshot)
        )

        val changedSnapshot = requestSnapshot.copy(
            pendingTasks = requestSnapshot.pendingTasks.map { it.copy(overdue = true) }
        )
        val rejected = DashboardAdviceRefreshPolicy.revalidate(
            success,
            changedSnapshot
        ) as AiDashboardAdviceResult.Failure
        assertEquals(AiDashboardAdviceFailureReason.CONTEXT_CHANGED, rejected.reason)
        assertTrue(rejected.recoverable)
    }

    private fun envelope(): AiDashboardAdviceEnvelope {
        return AiDashboardAdviceEnvelope(
            advice = AiDashboardAdvice(
                headline = "先处理临期任务",
                summary = "根据课程与任务安排生成。",
                items = listOf(
                    AiDashboardAdviceItem(
                        title = "完成实验",
                        detail = "先完成核心内容。",
                        priority = AiAdvicePriority.HIGH,
                        suggestedDate = "2026-06-08",
                        startTime = "10:00",
                        endTime = "11:00",
                        evidenceRefs = listOf("task:3")
                    )
                ),
                warnings = listOf("课程教学周尚未解析")
            ),
            targetDate = "2026-06-08",
            generatedAt = 1_781_000_000_000L,
            expiresAt = 1_781_021_600_000L,
            promptTag = LlmDashboardAdvicePromptFactory.PROMPT_TAG,
            providerName = "Test Provider",
            model = "test-model",
            contextFingerprint = "ab".repeat(32)
        )
    }
}
