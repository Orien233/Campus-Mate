package com.example.campusmate

import com.example.campusmate.domain.llm.LlmJsonPayloadExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmJsonPayloadExtractorTest {
    @Test
    fun extractObject_returnsPayloadFromFencedResponse() {
        val response = """
            下面是结果：
            ```json
            {"value":"ok"}
            ```
        """.trimIndent()

        assertEquals("""{"value":"ok"}""", LlmJsonPayloadExtractor.extractObject(response))
    }

    @Test
    fun extractObject_ignoresBracketsInsideQuotedStrings() {
        val payload = """{"text":"literal { [ ] } and \"quote\"","items":[1,2]}"""

        assertEquals(payload, LlmJsonPayloadExtractor.extractObject("prefix $payload suffix"))
    }

    @Test
    fun extractArray_returnsTopLevelArrayWithNoise() {
        assertEquals("[{\"id\":1},{\"id\":2}]", LlmJsonPayloadExtractor.extract("before [{\"id\":1},{\"id\":2}] after"))
    }

    @Test
    fun extract_returnsNullForBlankUnbalancedOrInvalidPayload() {
        assertNull(LlmJsonPayloadExtractor.extract("   "))
        assertNull(LlmJsonPayloadExtractor.extract("{\"plans\":["))
        assertNull(LlmJsonPayloadExtractor.extract("{]"))
    }

    @Test
    fun extract_skipsMismatchedCandidateAndFindsLaterJson() {
        assertEquals("""{"ok":true}""", LlmJsonPayloadExtractor.extract("{] noise {\"ok\":true}"))
    }
}
