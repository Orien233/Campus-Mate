package com.example.campusmate.domain.ai.memory

import com.example.campusmate.data.model.AiMemory

/** Expiry is the only automatic exclusion rule; reaching capacity never deletes memory. */
object AiMemoryRetentionPolicy {
    fun isStale(memory: AiMemory, nowMillis: Long): Boolean =
        !memory.isPinned && memory.expiresAt?.let { it <= nowMillis } == true

    fun isRetrievable(memory: AiMemory, nowMillis: Long): Boolean =
        memory.isEnabled && !isStale(memory, nowMillis)

    fun idsToRemove(memories: List<AiMemory>, nowMillis: Long): List<Long> =
        memories.asSequence()
            .filter { isStale(it, nowMillis) }
            .map(AiMemory::id)
            .filter { it > 0L }
            .distinct()
            .toList()
}
