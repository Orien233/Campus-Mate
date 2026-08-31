package com.example.campusmate.domain.ai.memory

import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.domain.ai.context.AiContextPurpose
import java.text.Normalizer
import java.util.Locale

/** Small deterministic lexical RAG; explicit pins/preferences can apply across subjects. */
object AiMemoryRetriever {
    const val MAX_FACT_CHARS = 300
    const val MAX_TOTAL_CHARS = 1_200

    fun retrieve(
        candidates: List<AiMemory>,
        purpose: AiContextPurpose,
        queryTerms: List<String>,
        nowMillis: Long,
        limit: Int = 4
    ): List<AiMemoryFact> {
        require(limit in 0..6) { "Memory limit must be between zero and six." }
        if (purpose == AiContextPurpose.FILE_ANALYSIS || limit == 0) return emptyList()
        val queryTokens = tokens(queryTerms.joinToString(" "))
        val ranked = candidates.asSequence()
            .filter { it.id > 0L && it.content.isNotBlank() }
            .filter { AiMemoryRetentionPolicy.isRetrievable(it, nowMillis) }
            .map { memory ->
                val matches = tokens(memory.content).count(queryTokens::contains)
                val generalPreference = memory.category == AiMemory.CATEGORY_PREFERENCE ||
                    memory.category == AiMemory.CATEGORY_CONSTRAINT
                val score = matches * 20 + (if (memory.isPinned) 30 else 0) +
                    (if (generalPreference) 15 else 0)
                memory to score
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<AiMemory, Int>> { it.second }
                    .thenByDescending { it.first.isPinned }
                    .thenByDescending { it.first.updatedAt }
                    .thenBy { it.first.id }
            )
            .toList()
        var remaining = MAX_TOTAL_CHARS
        return buildList {
            for ((memory, _) in ranked) {
                if (size >= limit || remaining == 0) break
                val content = memory.content.trim().replace(WHITESPACE, " ")
                    .take(minOf(MAX_FACT_CHARS, remaining))
                add(
                    AiMemoryFact(
                        localRef = "memory:${memory.id}",
                        category = categoryCode(memory.category),
                        content = content,
                        updatedAt = memory.updatedAt,
                        expiresAt = memory.expiresAt.takeUnless { memory.isPinned }
                    )
                )
                remaining -= content.length
            }
        }
    }

    private fun tokens(value: String): Set<String> {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        return buildSet {
            LATIN_WORD.findAll(normalized).forEach { add(it.value) }
            HAN_WORD.findAll(normalized).forEach { match ->
                if (match.value.length == 1) add(match.value)
                else match.value.windowed(2).forEach(::add)
            }
        }
    }

    private fun categoryCode(category: Int): String = when (category) {
        AiMemory.CATEGORY_GOAL -> "goal"
        AiMemory.CATEGORY_PREFERENCE -> "preference"
        AiMemory.CATEGORY_HABIT -> "habit"
        AiMemory.CATEGORY_CONSTRAINT -> "constraint"
        else -> "note"
    }

    private val LATIN_WORD = Regex("[a-z0-9]+")
    private val HAN_WORD = Regex("[\\p{IsHan}]+")
    private val WHITESPACE = Regex("\\s+")
}
