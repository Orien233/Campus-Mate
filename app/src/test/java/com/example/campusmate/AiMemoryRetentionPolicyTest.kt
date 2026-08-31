package com.example.campusmate

import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.domain.ai.memory.AiMemoryRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMemoryRetentionPolicyTest {
    @Test
    fun expiryBoundaryIsInclusiveAndNullNeverExpires() {
        assertTrue(AiMemoryRetentionPolicy.isStale(memory(expiresAt = 100L), 100L))
        assertFalse(AiMemoryRetentionPolicy.isStale(memory(expiresAt = 101L), 100L))
        assertFalse(AiMemoryRetentionPolicy.isStale(memory(), Long.MAX_VALUE))
    }

    @Test
    fun pinnedMemoryIsRetainedButDisabledMemoryIsNeverRetrieved() {
        val pinned = memory(expiresAt = 50L).copy(isPinned = true)
        assertFalse(AiMemoryRetentionPolicy.isStale(pinned, 100L))
        assertTrue(AiMemoryRetentionPolicy.isRetrievable(pinned, 100L))
        assertFalse(AiMemoryRetentionPolicy.isRetrievable(pinned.copy(isEnabled = false), 100L))
        assertFalse(AiMemoryRetentionPolicy.isRetrievable(memory().copy(isEnabled = false), 100L))
        assertFalse(AiMemoryRetentionPolicy.isRetrievable(memory(expiresAt = 100L), 100L))
    }

    @Test
    fun cleanupOnlyReturnsExpiredUnpinnedPersistedIds() {
        val memories = listOf(
            memory(id = 1L, expiresAt = 100L),
            memory(id = 2L, expiresAt = 90L).copy(isEnabled = false),
            memory(id = 3L, expiresAt = 90L).copy(isPinned = true),
            memory(id = 4L, expiresAt = 101L),
            memory(id = 5L),
            memory(id = 0L, expiresAt = 90L),
            memory(id = 1L, expiresAt = 100L)
        )
        assertEquals(listOf(1L, 2L), AiMemoryRetentionPolicy.idsToRemove(memories, 100L))
    }

    @Test
    fun capacityNeverEvictsUnexpiredMemories() {
        val memories = (1L..250L).map { memory(id = it) }
        assertTrue(AiMemoryRetentionPolicy.idsToRemove(memories, 100L).isEmpty())
    }

    private fun memory(id: Long = 1L, expiresAt: Long? = null) =
        AiMemory(id = id, content = "user goal", expiresAt = expiresAt)
}
