package com.example.campusmate

import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.domain.ai.context.AiContextPurpose
import com.example.campusmate.domain.ai.memory.AiMemoryContext
import com.example.campusmate.domain.ai.memory.AiMemoryContextRenderer
import com.example.campusmate.domain.ai.memory.AiMemoryRetriever
import org.junit.Assert.*
import org.junit.Test

class AiMemoryRetrieverTest {
    private val now = 10_000L
    private fun memory(id: Long, content: String) = AiMemory(id = id, content = content)
    private fun retrieve(items: List<AiMemory>, query: List<String> = listOf("高等数学 Python")) =
        AiMemoryRetriever.retrieve(items, AiContextPurpose.DASHBOARD_ADVICE, query, now)

    @Test fun matchesChineseBigramsAndNormalizesLatinWidthAndCase() {
        val result = retrieve(listOf(
            memory(1, "高等数学先做错题"),
            memory(2, "ＰＹＴＨＯＮ needs practice"),
            memory(3, "不相关的音乐笔记")
        ))
        assertEquals(setOf("memory:1", "memory:2"), result.map { it.localRef }.toSet())
    }

    @Test fun explicitPinsAndGeneralPreferencesCanApplyAcrossSubjects() {
        val result = retrieve(listOf(
            memory(1, "晚间需要照顾家人").copy(category = AiMemory.CATEGORY_CONSTRAINT),
            memory(2, "喜欢早晨学习").copy(category = AiMemory.CATEGORY_PREFERENCE),
            memory(3, "本学期练习口头表达").copy(isPinned = true),
            memory(4, "无关的旧备注")
        ), emptyList())
        assertEquals(listOf("memory:3", "memory:1", "memory:2"), result.map { it.localRef })
    }

    @Test fun disabledAlwaysWinsAndOnlyPinsOverrideExpiry() {
        val result = retrieve(listOf(
            memory(1, "数学过期").copy(expiresAt = now),
            memory(2, "数学禁用").copy(isEnabled = false, isPinned = true),
            memory(3, "数学置顶").copy(isPinned = true, expiresAt = now - 1),
            memory(4, "数学有效").copy(expiresAt = now + 1)
        ))
        assertEquals(setOf("memory:3", "memory:4"), result.map { it.localRef }.toSet())
        assertNull(result.first { it.localRef == "memory:3" }.expiresAt)
    }

    @Test fun rankingIsStableAndContextHasSmallBudget() {
        val items = (1L..8L).map { memory(it, "数学".repeat(250)).copy(updatedAt = 100) }
        val first = AiMemoryRetriever.retrieve(items, AiContextPurpose.PLAN_WEEK, listOf("数学"), now, 6)
        val reversed = AiMemoryRetriever.retrieve(items.reversed(), AiContextPurpose.PLAN_WEEK, listOf("数学"), now, 6)
        assertEquals(first, reversed)
        assertEquals(4, first.size)
        assertEquals(1_200, first.sumOf { it.content.length })
        assertTrue(first.all { it.content.length <= 300 })
        assertEquals("memory:1", first.first().localRef)
    }

    @Test fun fileAnalysisNeverRetrievesEvenPinnedMemory() {
        assertTrue(AiMemoryRetriever.retrieve(
            listOf(memory(1, "数学").copy(isPinned = true)),
            AiContextPurpose.FILE_ANALYSIS, listOf("数学"), now
        ).isEmpty())
    }

    @Test fun renderingDoesNotExposeScoringOrStorageFlags() {
        val facts = retrieve(listOf(memory(1, "数学练习").copy(isPinned = true)))
        val rendered = AiMemoryContextRenderer.render(AiMemoryContext(facts))
        assertTrue(rendered.contains("userManagedUntrustedMemories"))
        assertTrue(rendered.contains("memory:1"))
        listOf("isEnabled", "isPinned", "score", "sourceKey", "lastAccessed").forEach {
            assertFalse(rendered.contains(it))
        }
    }
}
