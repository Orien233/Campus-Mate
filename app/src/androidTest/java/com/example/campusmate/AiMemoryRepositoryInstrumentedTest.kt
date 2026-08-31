package com.example.campusmate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.model.AiMemoryDraft
import com.example.campusmate.data.repository.AiMemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiMemoryRepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun userCrudAndFlagsPreserveCreationTimeAndRespectExpiry() {
        val repository = AiMemoryRepository(context)
        val initialCount = repository.getMemoryCount()
        assumeTrue("Leave existing memories untouched when the store is full.", initialCount < AiMemory.MAX_MEMORY_COUNT)
        val id = repository.addUserMemory(AiMemoryDraft(content = "  repository test goal  "))
        assertTrue(id > 0L)
        try {
            val original = repository.getMemoryById(id)!!
            assertEquals("repository test goal", original.content)
            assertTrue(original.isEnabled)
            assertFalse(original.isPinned)
            assertTrue(original.createdAt > 0L)
            assertEquals(initialCount + 1, repository.getMemoryCount())
            assertTrue(repository.getMemoriesForManagement().any { it.id == id })

            assertTrue(repository.updateUserMemory(id, AiMemoryDraft(
                category = AiMemory.CATEGORY_PREFERENCE,
                content = "updated user preference",
                isEnabled = false,
                isPinned = true,
                expiresAt = 100L
            )))
            val updated = repository.getMemoryById(id)!!
            assertEquals(original.createdAt, updated.createdAt)
            assertTrue(updated.updatedAt >= original.updatedAt)
            assertEquals(AiMemory.CATEGORY_PREFERENCE, updated.category)
            assertFalse(repository.getActiveMemories(100L).any { it.id == id })

            assertTrue(repository.setEnabled(id, true))
            assertTrue(repository.getActiveMemories(100L).any { it.id == id })
            assertTrue(repository.setPinned(id, false))
            assertFalse(repository.getActiveMemories(100L).any { it.id == id })
            assertTrue(repository.getActiveMemories(99L).any { it.id == id })
            assertTrue(repository.countStale(100L) >= 1)
            assertTrue(repository.deleteMemory(id))
            assertNull(repository.getMemoryById(id))
        } finally {
            repository.deleteMemory(id)
        }
    }
}
