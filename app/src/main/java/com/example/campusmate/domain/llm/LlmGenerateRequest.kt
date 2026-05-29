package com.example.campusmate.domain.llm

data class LlmGenerateRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val responseJsonOnly: Boolean = true
)
