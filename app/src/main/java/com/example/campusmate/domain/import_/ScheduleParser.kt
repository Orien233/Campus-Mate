package com.example.campusmate.domain.import_

/** Strategy interface for parsing schedule HTML into course drafts. */
interface ScheduleParser {
    fun parse(html: String): List<CourseDraft>
}

class ScheduleParseException(message: String) : IllegalArgumentException(message)
