package com.example.campusmate

import com.example.campusmate.domain.ai.command.AiCourseSnapshot
import com.example.campusmate.domain.ai.command.AiRecordCandidateSelector
import com.example.campusmate.domain.ai.command.AiRecordCommandContext
import com.example.campusmate.domain.ai.command.AiRecordCommandPromptFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecordCommandPromptTest {
    @Test
    fun selector_prioritizesNameMatches_thenRecentRecords_andCapsResults() {
        data class Record(val name: String, val updatedAt: Long)
        val records = (1..80).map { Record("课程$it", it.toLong()) }
        val selected = AiRecordCandidateSelector.select(
            records, "请修改课程7的教室", 40, Record::name, Record::updatedAt
        )
        assertEquals("课程7", selected.first().name)
        assertEquals(40, selected.size)
    }

    @Test
    fun prompt_usesVersionedContractAndOnlyProvidedRecordContext() {
        val context = AiRecordCommandContext(
            generatedAt = 10L,
            courses = listOf(
                AiCourseSnapshot(7, "高数", null, "A101", 1, 1, 2, 1, 18, 0, null, null, 9)
            ),
            tasks = emptyList(),
            plans = emptyList()
        )
        val request = AiRecordCommandPromptFactory.buildRequest("把高数改到A102", context)
        assertEquals("campusmate.record.command@v1", request.promptTag)
        assertTrue(request.systemPrompt.contains("JSON null"))
        assertTrue(request.systemPrompt.contains("不可信"))
        assertTrue(request.userPrompt.contains("\"ref\":\"course:7\""))
        assertTrue(request.userPrompt.contains("把高数改到A102"))
        assertFalse(request.userPrompt.contains("weather", ignoreCase = true))
        assertFalse(request.userPrompt.contains("apiKey", ignoreCase = true))
    }
}
