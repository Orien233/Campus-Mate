package com.example.campusmate.domain.ai.file

import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.domain.llm.LlmInlineDataPolicy

object AiFileInputPolicy {
    const val MAX_TEXT_BYTES = 256 * 1024
    const val MAX_TEXT_CHARS = 120_000
    const val MAX_BINARY_BYTES = LlmInlineDataPolicy.MAX_ITEM_BYTES

    private val textMimeTypes = setOf(
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "text/csv",
        "text/html",
        "text/xml",
        "application/markdown",
        "application/csv",
        "application/json",
        "application/xml"
    )
    private val imageMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif"
    )
    private val textExtensions = setOf("txt", "md", "markdown", "csv", "json", "html", "htm", "xml")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

    fun resolveKind(mimeType: String?, displayName: String): AiFileKind? {
        val normalizedMimeType = canonicalMimeType(mimeType)
        if (normalizedMimeType.isNotBlank() && normalizedMimeType != "application/octet-stream") {
            return when (normalizedMimeType) {
                in textMimeTypes -> AiFileKind.TEXT
                in imageMimeTypes -> AiFileKind.IMAGE
                "application/pdf" -> AiFileKind.PDF
                else -> null
            }
        }
        return when (extension(displayName)) {
            in textExtensions -> AiFileKind.TEXT
            in imageExtensions -> AiFileKind.IMAGE
            "pdf" -> AiFileKind.PDF
            else -> null
        }
    }

    fun normalizedMimeType(kind: AiFileKind, mimeType: String?, displayName: String): String {
        val normalized = canonicalMimeType(mimeType)
        val allowedForKind = when (kind) {
            AiFileKind.TEXT -> normalized in textMimeTypes
            AiFileKind.IMAGE -> normalized in imageMimeTypes
            AiFileKind.PDF -> normalized == "application/pdf"
        }
        if (allowedForKind) return normalized
        return when (kind) {
            AiFileKind.TEXT -> when (extension(displayName)) {
                "md", "markdown" -> "text/markdown"
                "csv" -> "text/csv"
                "json" -> "application/json"
                "html", "htm" -> "text/html"
                "xml" -> "application/xml"
                else -> "text/plain"
            }
            AiFileKind.IMAGE -> when (extension(displayName)) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            AiFileKind.PDF -> "application/pdf"
        }
    }

    fun validate(
        kind: AiFileKind,
        sizeBytes: Long?,
        capability: LlmMultimodalCapability
    ): AiFileInputError? {
        if (sizeBytes == 0L) return AiFileInputError.EMPTY_FILE
        return when (kind) {
            AiFileKind.TEXT -> {
                if (sizeBytes != null && sizeBytes > MAX_TEXT_BYTES) {
                    AiFileInputError.TEXT_TOO_LARGE
                } else {
                    null
                }
            }
            AiFileKind.IMAGE -> when {
                !capability.supportsMimeType("image/jpeg") ->
                    AiFileInputError.CAPABILITY_MISMATCH
                sizeBytes != null && sizeBytes > MAX_BINARY_BYTES ->
                    AiFileInputError.BINARY_TOO_LARGE
                else -> null
            }
            AiFileKind.PDF -> when {
                !capability.supportsMimeType("application/pdf") ->
                    AiFileInputError.CAPABILITY_MISMATCH
                sizeBytes != null && sizeBytes > MAX_BINARY_BYTES ->
                    AiFileInputError.BINARY_TOO_LARGE
                else -> null
            }
        }
    }

    private fun extension(displayName: String): String {
        return displayName.substringAfterLast('.', "").trim().lowercase()
    }

    private fun canonicalMimeType(mimeType: String?): String {
        return when (val normalized = mimeType?.trim()?.lowercase().orEmpty()) {
            "image/jpg" -> "image/jpeg"
            else -> normalized
        }
    }
}
