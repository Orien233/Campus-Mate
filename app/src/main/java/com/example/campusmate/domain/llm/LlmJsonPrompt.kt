package com.example.campusmate.domain.llm

internal object LlmJsonPrompt {
    private const val JSON_ONLY_INSTRUCTION =
        "只返回一个合法 JSON 对象或数组，不要输出 markdown、代码块、解释或任何前后缀文本。"

    fun buildSystemPrompt(request: LlmGenerateRequest): String {
        val basePrompt = request.systemPrompt.trim()
        return listOf(
            "[CampusMate prompt=${request.promptTag}]",
            basePrompt,
            JSON_ONLY_INSTRUCTION.takeIf { request.responseJsonOnly }
        )
            .filterNotNull()
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")
    }
}
