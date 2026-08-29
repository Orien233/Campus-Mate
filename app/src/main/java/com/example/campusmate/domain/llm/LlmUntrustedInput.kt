package com.example.campusmate.domain.llm

object LlmUntrustedInput {
    const val DEFAULT_MAX_CHARS = 60_000
    private val BOUNDARY_PATTERN = Regex("""(?i)</?\s*UNTRUSTED_DATA\b[^>]*>""")

    fun wrap(
        label: String,
        content: String,
        maxChars: Int = DEFAULT_MAX_CHARS
    ): String {
        require(maxChars > 0) { "maxChars must be positive" }
        val safeLabel = label
            .uppercase()
            .replace(Regex("[^A-Z0-9_-]"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "INPUT" }
        val wasTruncated = content.length > maxChars
        val sanitized = BOUNDARY_PATTERN
            .replace(content.take(maxChars), "<FILTERED_UNTRUSTED_MARKER>")
            .trim()
        val clipped = buildString {
            append(sanitized)
            if (wasTruncated) {
                if (isNotEmpty()) appendLine()
                append("[内容已在本机截断]")
            }
        }
        return """
            <UNTRUSTED_DATA label="$safeLabel">
            $clipped
            </UNTRUSTED_DATA>
        """.trimIndent()
    }
}
