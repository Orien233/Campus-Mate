package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.domain.llm.LlmInlineData
import com.example.campusmate.domain.llm.LlmInlineDataError
import com.example.campusmate.domain.llm.LlmInlineDataPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmInlineDataPolicyTest {
    @Test
    fun emptyRequestKeepsTextOnlyCompatibility() {
        assertNull(
            LlmInlineDataPolicy.validate(
                emptyList(),
                LlmMultimodalCapability.TEXT_ONLY
            )
        )
    }

    @Test
    fun capabilityMustExplicitlyCoverMimeType() {
        val image = LlmInlineData("image/jpeg", byteArrayOf(1))
        val pdf = LlmInlineData("application/pdf", byteArrayOf(1))

        assertEquals(
            LlmInlineDataError.CAPABILITY_MISMATCH,
            LlmInlineDataPolicy.validate(listOf(image), LlmMultimodalCapability.TEXT_ONLY)
        )
        assertNull(
            LlmInlineDataPolicy.validate(listOf(image), LlmMultimodalCapability.IMAGE_INPUT)
        )
        assertEquals(
            LlmInlineDataError.CAPABILITY_MISMATCH,
            LlmInlineDataPolicy.validate(listOf(pdf), LlmMultimodalCapability.IMAGE_INPUT)
        )
        assertNull(
            LlmInlineDataPolicy.validate(
                listOf(pdf),
                LlmMultimodalCapability.IMAGE_AND_FILE_INPUT
            )
        )
    }

    @Test
    fun rejectsUnknownEmptyOversizedAndMultipleInputs() {
        assertEquals(
            LlmInlineDataError.EMPTY_DATA,
            LlmInlineDataPolicy.validate(
                listOf(LlmInlineData("image/png", byteArrayOf())),
                LlmMultimodalCapability.IMAGE_INPUT
            )
        )
        assertEquals(
            LlmInlineDataError.UNSUPPORTED_MIME,
            LlmInlineDataPolicy.validate(
                listOf(LlmInlineData("application/zip", byteArrayOf(1))),
                LlmMultimodalCapability.IMAGE_AND_FILE_INPUT
            )
        )
        assertEquals(
            LlmInlineDataError.ITEM_TOO_LARGE,
            LlmInlineDataPolicy.validate(
                listOf(
                    LlmInlineData(
                        "image/png",
                        ByteArray(LlmInlineDataPolicy.MAX_ITEM_BYTES + 1)
                    )
                ),
                LlmMultimodalCapability.IMAGE_INPUT
            )
        )
        assertEquals(
            LlmInlineDataError.TOO_MANY_ITEMS,
            LlmInlineDataPolicy.validate(
                listOf(
                    LlmInlineData("image/png", byteArrayOf(1)),
                    LlmInlineData("image/png", byteArrayOf(2))
                ),
                LlmMultimodalCapability.IMAGE_INPUT
            )
        )
    }
}
