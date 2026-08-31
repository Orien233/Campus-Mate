package com.example.campusmate.data.model

/** Local text explicitly entered and managed by the user; never a raw model response. */
data class AiMemory(
    val id: Long = 0L,
    val category: Int = CATEGORY_OTHER,
    val content: String,
    val isEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val expiresAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toDraft(): AiMemoryDraft = AiMemoryDraft(category, content, isEnabled, isPinned, expiresAt)

    companion object {
        const val CATEGORY_GOAL = 0
        const val CATEGORY_PREFERENCE = 1
        const val CATEGORY_HABIT = 2
        const val CATEGORY_CONSTRAINT = 3
        const val CATEGORY_OTHER = 4
        const val MAX_CONTENT_LENGTH = 500
        const val MAX_MEMORY_COUNT = 200

        val validCategories = setOf(
            CATEGORY_GOAL,
            CATEGORY_PREFERENCE,
            CATEGORY_HABIT,
            CATEGORY_CONSTRAINT,
            CATEGORY_OTHER
        )
    }
}

/** Only user-editable fields; identifiers and audit timestamps are owned by the repository. */
data class AiMemoryDraft(
    val category: Int = AiMemory.CATEGORY_OTHER,
    val content: String,
    val isEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val expiresAt: Long? = null
)
