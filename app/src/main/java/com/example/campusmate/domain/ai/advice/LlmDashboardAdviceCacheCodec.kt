package com.example.campusmate.domain.ai.advice

import com.example.campusmate.util.DateTimeUtils
import org.json.JSONArray
import org.json.JSONObject

object LlmDashboardAdviceCacheCodec {
    fun encode(envelope: AiDashboardAdviceEnvelope): String {
        return JSONObject()
            .put("schemaVersion", CACHE_SCHEMA_VERSION)
            .put("targetDate", envelope.targetDate)
            .put("generatedAt", envelope.generatedAt)
            .put("expiresAt", envelope.expiresAt)
            .put("promptTag", envelope.promptTag)
            .put("providerName", envelope.providerName)
            .put("model", envelope.model)
            .put("contextFingerprint", envelope.contextFingerprint)
            .put("advice", envelope.advice.toJson())
            .toString()
    }

    fun decode(raw: String): AiDashboardAdviceEnvelope? {
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion") != CACHE_SCHEMA_VERSION) return null
            val targetDate = root.optString("targetDate").takeIf(::isStrictDate) ?: return null
            val generatedAt = root.optLong("generatedAt").takeIf { it > 0L } ?: return null
            val expiresAt = root.optLong("expiresAt").takeIf { it > generatedAt } ?: return null
            val promptTag = root.optString("promptTag")
                .takeIf { it == LlmDashboardAdvicePromptFactory.PROMPT_TAG }
                ?: return null
            val providerName = bounded(root.optString("providerName"), MAX_PROVENANCE_CHARS)
                .takeIf(String::isNotBlank) ?: return null
            val model = bounded(root.optString("model"), MAX_PROVENANCE_CHARS)
                .takeIf(String::isNotBlank) ?: return null
            val fingerprint = root.optString("contextFingerprint")
                .takeIf(FINGERPRINT_PATTERN::matches) ?: return null
            val advice = root.optJSONObject("advice")?.toAdvice() ?: return null

            AiDashboardAdviceEnvelope(
                advice = advice,
                targetDate = targetDate,
                generatedAt = generatedAt,
                expiresAt = expiresAt,
                promptTag = promptTag,
                providerName = providerName,
                model = model,
                contextFingerprint = fingerprint
            )
        }.getOrNull()
    }

    private fun AiDashboardAdvice.toJson(): JSONObject {
        return JSONObject()
            .put("headline", headline)
            .put("summary", summary)
            .put(
                "items",
                JSONArray().apply {
                    items.forEach { item ->
                        put(
                            JSONObject()
                                .put("title", item.title)
                                .put("detail", item.detail)
                                .put("priority", item.priority.wireValue)
                                .put("suggestedDate", item.suggestedDate ?: JSONObject.NULL)
                                .put("startTime", item.startTime ?: JSONObject.NULL)
                                .put("endTime", item.endTime ?: JSONObject.NULL)
                                .put(
                                    "evidenceRefs",
                                    JSONArray().apply { item.evidenceRefs.forEach(::put) }
                                )
                        )
                    }
                }
            )
            .put("warnings", JSONArray().apply { warnings.forEach(::put) })
    }

    private fun JSONObject.toAdvice(): AiDashboardAdvice? {
        val headline = bounded(optString("headline"), MAX_HEADLINE_CHARS)
            .takeIf(String::isNotBlank) ?: return null
        val summary = bounded(optString("summary"), MAX_SUMMARY_CHARS)
            .takeIf(String::isNotBlank) ?: return null
        val itemsJson = optJSONArray("items") ?: return null
        val items = (0 until minOf(itemsJson.length(), MAX_ITEMS))
            .mapNotNull { index -> itemsJson.optJSONObject(index)?.toItem() }
            .distinctBy { it.title.lowercase() + "\u0000" + it.detail.lowercase() }
        if (items.isEmpty()) return null
        val warnings = readStrings(optJSONArray("warnings"), MAX_WARNINGS)
            .map { bounded(it, MAX_WARNING_CHARS) }
            .filter(String::isNotBlank)
            .distinct()
        return AiDashboardAdvice(headline, summary, items, warnings)
    }

    private fun JSONObject.toItem(): AiDashboardAdviceItem? {
        val title = bounded(optString("title"), MAX_ITEM_TITLE_CHARS)
            .takeIf(String::isNotBlank) ?: return null
        val detail = bounded(optString("detail"), MAX_ITEM_DETAIL_CHARS)
            .takeIf(String::isNotBlank) ?: return null
        val priority = AiAdvicePriority.fromWireValue(optString("priority")) ?: return null
        val suggestedDate = nullableString("suggestedDate")
        if (suggestedDate != null && !isStrictDate(suggestedDate)) return null
        val startTime = nullableString("startTime")
        val endTime = nullableString("endTime")
        if ((startTime == null) != (endTime == null)) return null
        if (startTime != null && (!isTime(startTime) || !isTime(endTime.orEmpty()))) return null
        if (startTime != null && suggestedDate == null) return null
        if (startTime != null && parseMinutes(endTime.orEmpty()) <= parseMinutes(startTime)) return null
        val evidenceRefs = readStrings(optJSONArray("evidenceRefs"), MAX_SCANNED_STRINGS)
            .filter(ALLOWED_CACHE_REF::matches)
            .distinct()
            .take(MAX_EVIDENCE_REFS)
        if (evidenceRefs.isEmpty()) return null
        return AiDashboardAdviceItem(
            title = title,
            detail = detail,
            priority = priority,
            suggestedDate = suggestedDate,
            startTime = startTime,
            endTime = endTime,
            evidenceRefs = evidenceRefs
        )
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).trim().takeIf(String::isNotBlank)
    }

    private fun readStrings(array: JSONArray?, limit: Int): List<String> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), MAX_SCANNED_STRINGS))
            .mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
            .take(limit)
    }

    private fun bounded(value: String, maxChars: Int): String {
        return value.trim().replace(WHITESPACE, " ").take(maxChars)
    }

    private fun isStrictDate(value: String): Boolean {
        return DATE_PATTERN.matches(value) && DateTimeUtils.parseDateMillis(value) != null
    }

    private fun isTime(value: String): Boolean {
        val match = TIME_PATTERN.matchEntire(value) ?: return false
        val hour = match.groupValues[1].toIntOrNull() ?: return false
        val minute = match.groupValues[2].toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
    }

    private fun parseMinutes(value: String): Int {
        val match = requireNotNull(TIME_PATTERN.matchEntire(value))
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }

    private const val CACHE_SCHEMA_VERSION = 1
    private const val MAX_PROVENANCE_CHARS = 80
    private const val MAX_HEADLINE_CHARS = 60
    private const val MAX_SUMMARY_CHARS = 240
    private const val MAX_ITEM_TITLE_CHARS = 50
    private const val MAX_ITEM_DETAIL_CHARS = 180
    private const val MAX_WARNING_CHARS = 160
    private const val MAX_ITEMS = 3
    private const val MAX_WARNINGS = 6
    private const val MAX_EVIDENCE_REFS = 4
    private const val MAX_SCANNED_STRINGS = 24
    private val WHITESPACE = Regex("""\s+""")
    private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")
    private val FINGERPRINT_PATTERN = Regex("""[0-9a-f]{64}""")
    private val ALLOWED_CACHE_REF = Regex(
        """(?:(?:course|task|plan):\d+|settings:daily-goal|learning:recent|weather:current|schedule:occupied)"""
    )
}
