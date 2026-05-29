package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmAuthHeaderType
import com.example.campusmate.data.model.llm.LlmProviderType
import com.example.campusmate.domain.llm.LlmProviderPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProviderPresetsTest {
    @Test
    fun requiredPresetsExist() {
        assertNotNull(LlmProviderPresets.findById("deepseek"))
        assertNotNull(LlmProviderPresets.findById("mimo"))
        assertNotNull(LlmProviderPresets.findById("custom"))
    }

    @Test
    fun geminiUsesGoogleApiKeyHeader() {
        val preset = requireNotNull(LlmProviderPresets.findById("gemini"))

        assertEquals(LlmProviderType.GEMINI, preset.providerType)
        assertEquals(LlmAuthHeaderType.X_GOOG_API_KEY, preset.authHeaderType)
    }

    @Test
    fun openAiCompatiblePresetsUseBearerAuthorization() {
        val compatiblePresets = LlmProviderPresets.all
            .filter { it.providerType == LlmProviderType.OPENAI_COMPATIBLE }

        assertTrue(compatiblePresets.isNotEmpty())
        compatiblePresets.forEach { preset ->
            assertEquals(LlmAuthHeaderType.BEARER_AUTHORIZATION, preset.authHeaderType)
        }
    }

    @Test
    fun presetIdsAreUnique() {
        val ids = LlmProviderPresets.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }
}
