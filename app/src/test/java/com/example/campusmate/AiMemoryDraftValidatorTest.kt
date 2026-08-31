package com.example.campusmate

import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.model.AiMemoryDraft
import com.example.campusmate.domain.ai.memory.AiMemoryDraftError
import com.example.campusmate.domain.ai.memory.AiMemoryDraftValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMemoryDraftValidatorTest {
    @Test
    fun acceptsAllFiveCategoriesAndTrimsOnlyOuterWhitespace() {
        for (category in 0..4) {
            val draft = AiMemoryDraft(category = category, content = "  study\nregularly  ")
            assertNull(AiMemoryDraftValidator.validate(draft))
            assertEquals("study\nregularly", AiMemoryDraftValidator.requireValid(draft).content)
        }
    }

    @Test
    fun rejectsUnknownCategoryAndEmptyContent() {
        assertEquals(AiMemoryDraftError.INVALID_CATEGORY,
            AiMemoryDraftValidator.validate(AiMemoryDraft(category = -1, content = "goal")))
        assertEquals(AiMemoryDraftError.INVALID_CATEGORY,
            AiMemoryDraftValidator.validate(AiMemoryDraft(category = 5, content = "goal")))
        assertEquals(AiMemoryDraftError.EMPTY_CONTENT,
            AiMemoryDraftValidator.validate(AiMemoryDraft(content = " \n\t ")))
    }

    @Test
    fun enforcesFiveHundredCharacterBoundary() {
        assertNull(AiMemoryDraftValidator.validate(AiMemoryDraft(content = "a".repeat(500))))
        assertEquals(AiMemoryDraftError.CONTENT_TOO_LONG,
            AiMemoryDraftValidator.validate(AiMemoryDraft(content = "a".repeat(501))))
    }

    @Test
    fun acceptsNoExpiryOrPastExpiryButRejectsInvalidTimestamps() {
        assertNull(AiMemoryDraftValidator.validate(AiMemoryDraft(content = "goal")))
        assertNull(AiMemoryDraftValidator.validate(AiMemoryDraft(content = "goal", expiresAt = 1L)))
        assertEquals(AiMemoryDraftError.INVALID_EXPIRY,
            AiMemoryDraftValidator.validate(AiMemoryDraft(content = "goal", expiresAt = 0L)))
        assertEquals(AiMemoryDraftError.INVALID_EXPIRY,
            AiMemoryDraftValidator.validate(AiMemoryDraft(content = "goal", expiresAt = -1L)))
    }

    @Test
    fun defaultsAndDraftConversionKeepOnlyEditableValues() {
        val default = AiMemoryDraft(content = "goal")
        assertTrue(default.isEnabled)
        assertFalse(default.isPinned)
        val memory = AiMemory(id = 7L, content = "goal", isEnabled = false, isPinned = true,
            expiresAt = 200L, createdAt = 100L, updatedAt = 150L)
        assertEquals(AiMemoryDraft(content = "goal", isEnabled = false, isPinned = true,
            expiresAt = 200L), memory.toDraft())
    }

    @Test
    fun allowsLastAvailableSlot() {
        AiMemoryDraftValidator.requireCapacity(199)
        assertEquals(200, AiMemory.MAX_MEMORY_COUNT)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsInsertionAtCapacity() {
        AiMemoryDraftValidator.requireCapacity(200)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDraftBeforeWriting() {
        AiMemoryDraftValidator.requireValid(AiMemoryDraft(content = " "))
    }
}
