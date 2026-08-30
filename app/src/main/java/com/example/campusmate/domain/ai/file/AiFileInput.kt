package com.example.campusmate.domain.ai.file

import com.example.campusmate.domain.llm.LlmInlineData

const val AI_FILE_SELECTION_REF = "file:selection:1"

sealed class AiFileInput {
    val fileRef: String
        get() = AI_FILE_SELECTION_REF
    abstract val mimeType: String
    abstract val sizeBytes: Long

    data class Text(
        override val mimeType: String,
        override val sizeBytes: Long,
        val content: String
    ) : AiFileInput()

    data class Inline(
        val data: LlmInlineData
    ) : AiFileInput() {
        override val mimeType: String
            get() = data.mimeType
        override val sizeBytes: Long
            get() = data.bytes.size.toLong()
    }
}

data class AiSelectedFile(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?
)

enum class AiFileKind {
    TEXT,
    IMAGE,
    PDF
}

enum class AiFileInputError {
    UNSUPPORTED_TYPE,
    CAPABILITY_MISMATCH,
    EMPTY_FILE,
    TEXT_TOO_LARGE,
    BINARY_TOO_LARGE,
    TEXT_TOO_LONG,
    READ_FAILED
}

class AiFileReadException(
    val error: AiFileInputError,
    cause: Throwable? = null
) : IllegalArgumentException(error.name, cause)
