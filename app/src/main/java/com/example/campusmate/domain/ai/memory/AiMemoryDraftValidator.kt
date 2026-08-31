package com.example.campusmate.domain.ai.memory

import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.model.AiMemoryDraft

enum class AiMemoryDraftError {
    INVALID_CATEGORY,
    EMPTY_CONTENT,
    CONTENT_TOO_LONG,
    INVALID_EXPIRY
}

/** Pure validation; errors never include the user's memory content. */
object AiMemoryDraftValidator {
    fun validate(draft: AiMemoryDraft): AiMemoryDraftError? {
        if (draft.category !in AiMemory.validCategories) return AiMemoryDraftError.INVALID_CATEGORY
        val content = draft.content.trim()
        if (content.isBlank()) return AiMemoryDraftError.EMPTY_CONTENT
        if (content.length > AiMemory.MAX_CONTENT_LENGTH) return AiMemoryDraftError.CONTENT_TOO_LONG
        if (draft.expiresAt != null && draft.expiresAt <= 0L) return AiMemoryDraftError.INVALID_EXPIRY
        return null
    }

    fun requireValid(draft: AiMemoryDraft): AiMemoryDraft {
        val error = validate(draft)
        require(error == null) { "Invalid AI memory draft: $error" }
        return draft.copy(content = draft.content.trim())
    }

    fun requireCapacity(currentCount: Int) {
        check(currentCount < AiMemory.MAX_MEMORY_COUNT) { "AI memory limit reached (200)." }
    }
}
