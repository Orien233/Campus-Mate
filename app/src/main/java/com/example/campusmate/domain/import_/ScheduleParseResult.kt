package com.example.campusmate.domain.import_

/** Result returned by either LLM-assisted or local schedule parsing. */
data class ScheduleParseResult(
    val drafts: List<CourseDraft>,
    val warnings: List<String> = emptyList(),
    val parserLabel: String,
    val usedLlm: Boolean,
    val fallbackReason: String? = null,
    val sectionTimeSlots: List<SectionTimeSlot> = emptyList()
)

/**
 * Optional "section -> clock time range" extracted from some academic systems (e.g. BJTU).
 *
 * This is not course data and is only applied after user confirms import in the preview screen.
 */
data class SectionTimeSlot(
    val section: Int,
    val startTime: String,
    val endTime: String
) : java.io.Serializable
