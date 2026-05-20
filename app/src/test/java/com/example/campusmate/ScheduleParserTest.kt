package com.example.campusmate

import com.example.campusmate.domain.import_.JsoupScheduleParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {
    @Test
    fun jsoupScheduleParser_parsesSampleScheduleHtml() {
        val sampleHtml = readSampleScheduleHtml()
        val drafts = JsoupScheduleParser().parse(sampleHtml)

        assertEquals(6, drafts.size)
        assertTrue(drafts.any { it.name == "高等数学" && it.weekday == 1 && it.startSection == 1 && it.endSection == 2 })
        assertTrue(drafts.any { it.name.contains("冲突示例") && it.startWeek == 8 && it.endWeek == 12 })
    }

    private fun readSampleScheduleHtml(): String {
        val candidates = listOf(
            File("src/main/assets/sample_schedule.html"),
            File("app/src/main/assets/sample_schedule.html")
        )
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }
}
