package com.example.campusmate.domain.llm

import com.example.campusmate.data.model.llm.LlmMultimodalCapability

object LlmInlineDataPolicy {
    const val MAX_ITEM_COUNT = 1
    const val MAX_ITEM_BYTES = 8 * 1024 * 1024
    const val MAX_TOTAL_BYTES = MAX_ITEM_BYTES

    private val supportedMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "application/pdf"
    )

    fun validate(
        items: List<LlmInlineData>,
        capability: LlmMultimodalCapability
    ): LlmInlineDataError? {
        if (items.size > MAX_ITEM_COUNT) return LlmInlineDataError.TOO_MANY_ITEMS
        if (items.any { it.bytes.isEmpty() }) return LlmInlineDataError.EMPTY_DATA
        if (items.any { it.bytes.size > MAX_ITEM_BYTES }) return LlmInlineDataError.ITEM_TOO_LARGE
        if (items.sumOf { it.bytes.size.toLong() } > MAX_TOTAL_BYTES) {
            return LlmInlineDataError.TOTAL_TOO_LARGE
        }
        if (items.any { it.normalizedMimeType() !in supportedMimeTypes }) {
            return LlmInlineDataError.UNSUPPORTED_MIME
        }
        if (items.any { !capability.supportsMimeType(it.normalizedMimeType()) }) {
            return LlmInlineDataError.CAPABILITY_MISMATCH
        }
        return null
    }

    fun requireValid(
        items: List<LlmInlineData>,
        capability: LlmMultimodalCapability
    ) {
        val error = validate(items, capability) ?: return
        throw IllegalArgumentException("Invalid inline LLM input: ${error.name}")
    }

    private fun LlmInlineData.normalizedMimeType(): String = mimeType.trim().lowercase()
}
