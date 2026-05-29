package com.example.campusmate.domain.llm

sealed class LlmGenerateResult {
    data class Success(
        val text: String,
        val rawJson: String? = null,
        val providerName: String,
        val model: String
    ) : LlmGenerateResult()

    data class Failure(
        val message: String,
        val detail: String? = null,
        val recoverable: Boolean = true
    ) : LlmGenerateResult()
}
