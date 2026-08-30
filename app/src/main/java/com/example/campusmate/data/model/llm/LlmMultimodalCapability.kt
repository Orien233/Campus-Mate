package com.example.campusmate.data.model.llm

/** User-declared capability of the currently configured endpoint and model. */
enum class LlmMultimodalCapability {
    TEXT_ONLY,
    IMAGE_INPUT,
    IMAGE_AND_FILE_INPUT;

    fun supportsMimeType(mimeType: String): Boolean {
        val normalized = mimeType.trim().lowercase()
        return when {
            normalized.startsWith("image/") -> this != TEXT_ONLY
            normalized == "application/pdf" -> this == IMAGE_AND_FILE_INPUT
            else -> false
        }
    }
}
