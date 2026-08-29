package com.example.campusmate.domain.llm

import org.json.JSONArray
import org.json.JSONObject

object LlmJsonPayloadExtractor {
    fun extract(rawText: String): String? {
        return extractFirst(rawText, setOf('{', '['))
    }

    fun extractObject(rawText: String): String? {
        return extractFirst(rawText, setOf('{'))
    }

    fun extractArray(rawText: String): String? {
        return extractFirst(rawText, setOf('['))
    }

    private fun extractFirst(rawText: String, allowedOpenings: Set<Char>): String? {
        val text = rawText.take(MAX_SCAN_CHARS).trim()
        if (text.isBlank()) return null

        var candidateCount = 0
        text.indices.forEach { start ->
            val opening = text[start]
            if (opening !in allowedOpenings) return@forEach
            candidateCount += 1
            if (candidateCount > MAX_CANDIDATE_STARTS) return null
            val candidate = balancedCandidate(text, start) ?: return@forEach
            val valid = runCatching {
                if (opening == '{') JSONObject(candidate) else JSONArray(candidate)
            }.isSuccess
            if (valid) return candidate
        }
        return null
    }

    private fun balancedCandidate(text: String, start: Int): String? {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false

        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{', '[' -> stack.addLast(char)
                '}', ']' -> {
                    val expected = if (char == '}') '{' else '['
                    if (stack.isEmpty() || stack.removeLast() != expected) return null
                    if (stack.isEmpty()) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private const val MAX_SCAN_CHARS = 256_000
    private const val MAX_CANDIDATE_STARTS = 32
}
