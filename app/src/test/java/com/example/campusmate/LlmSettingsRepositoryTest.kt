package com.example.campusmate

import com.example.campusmate.data.repository.LlmSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmSettingsRepositoryTest {
    @Test
    fun maskApiKeyShowsOnlyEdges() {
        val masked = LlmSettingsRepository.maskApiKey("abcd12345678wxyz")

        assertEquals("abcd\u2022\u2022\u2022\u2022wxyz", masked)
        assertFalse(masked.contains("12345678"))
    }

    @Test
    fun maskApiKeyHidesShortKeys() {
        assertEquals("\u2022\u2022\u2022\u2022", LlmSettingsRepository.maskApiKey("short"))
    }

    @Test
    fun maskApiKeyReturnsBlankForMissingKey() {
        assertEquals("", LlmSettingsRepository.maskApiKey(null))
        assertEquals("", LlmSettingsRepository.maskApiKey(" "))
    }

    @Test
    fun hasMaskedShapeDetectsMaskedValue() {
        assertTrue(LlmSettingsRepository.hasMaskedShape("abcd\u2022\u2022\u2022\u2022wxyz"))
        assertFalse(LlmSettingsRepository.hasMaskedShape("plain-api-key"))
    }
}
