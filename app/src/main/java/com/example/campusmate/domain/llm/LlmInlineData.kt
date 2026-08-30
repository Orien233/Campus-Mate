package com.example.campusmate.domain.llm

/**
 * Transient binary input for one LLM request.
 *
 * The object intentionally carries neither a Uri nor the user's original filename so request
 * builders cannot leak a local path or persistable document reference.
 */
data class LlmInlineData(
    val mimeType: String,
    val bytes: ByteArray
)

enum class LlmInlineDataError {
    TOO_MANY_ITEMS,
    EMPTY_DATA,
    ITEM_TOO_LARGE,
    TOTAL_TOO_LARGE,
    UNSUPPORTED_MIME,
    CAPABILITY_MISMATCH
}
