package com.example.campusmate.domain.llm

data class LlmGenerateRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val responseJsonOnly: Boolean = true,
    val promptId: String = DEFAULT_PROMPT_ID,
    val promptVersion: Int = 1
) {
    init {
        require(promptId.matches(PROMPT_ID_PATTERN)) {
            "promptId must be 1-80 characters, start with a lowercase letter or digit, and use only dots, underscores, or hyphens"
        }
        require(promptVersion > 0) { "promptVersion must be positive" }
    }

    val promptTag: String
        get() = "$promptId@v$promptVersion"

    companion object {
        private const val DEFAULT_PROMPT_ID = "campusmate.generic"
        private val PROMPT_ID_PATTERN = Regex("""[a-z0-9][a-z0-9._-]{0,79}""")
    }
}
