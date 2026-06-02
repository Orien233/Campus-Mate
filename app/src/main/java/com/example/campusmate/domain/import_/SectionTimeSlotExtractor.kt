package com.example.campusmate.domain.import_

/**
 * Best-effort extractor for "section -> clock time range" information from imported HTML.
 *
 * BJTU pages often embed time slots in a JS object like:
 *   courseNum: 1, startTime: '08:00', endTime: '09:50'
 *
 * If nothing is found, returns empty list.
 */
object SectionTimeSlotExtractor {
    // Table header style:
    //   第1节 <br> <span class="text-muted">[08:00-09:50]</span>
    private val tablePattern = Regex(
        pattern = """第\s*(\d+)\s*节[\s\S]*?\[(\d{1,2}:\d{2})\s*[-~—]\s*(\d{1,2}:\d{2})\]""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val bjtuPattern = Regex(
        pattern = """courseNum\s*[:=]\s*(\d+)\s*,\s*startTime\s*[:=]\s*['"](\d{1,2}:\d{2})['"]\s*,\s*endTime\s*[:=]\s*['"](\d{1,2}:\d{2})['"]""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val bjtuJsonPattern = Regex(
        pattern = """"courseNum"\s*:\s*"?(\d+)"?\s*,\s*"startTime"\s*:\s*"(\d{1,2}:\d{2})"\s*,\s*"endTime"\s*:\s*"(\d{1,2}:\d{2})"""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun extract(html: String): List<SectionTimeSlot> {
        if (html.isBlank()) return emptyList()
        val slots = mutableListOf<SectionTimeSlot>()
        fun addMatches(pattern: Regex) {
            for (m in pattern.findAll(html)) {
                val section = m.groupValues[1].toIntOrNull() ?: continue
                val start = m.groupValues[2]
                val end = m.groupValues[3]
                if (section > 0) {
                    slots.add(SectionTimeSlot(section = section, startTime = start, endTime = end))
                }
            }
        }
        addMatches(tablePattern)
        addMatches(bjtuPattern)
        addMatches(bjtuJsonPattern)
        return slots.distinctBy { it.section }.sortedBy { it.section }
    }
}
