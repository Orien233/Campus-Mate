package com.example.campusmate.domain.llm

internal object LlmJsonPrompt {
    private const val JSON_ONLY_INSTRUCTION = "\u53ea\u8fd4\u56de JSON\uff0c\u4e0d\u8981 markdown\uff0c\u4e0d\u8981\u89e3\u91ca\u3002"

    fun buildSystemPrompt(request: LlmGenerateRequest): String {
        val basePrompt = request.systemPrompt.trim()
        if (!request.responseJsonOnly) return basePrompt
        return listOf(basePrompt, JSON_ONLY_INSTRUCTION)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
    }
}
