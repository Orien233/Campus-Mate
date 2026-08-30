package com.example.campusmate

import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.domain.ai.file.AiFileInputError
import com.example.campusmate.domain.ai.file.AiFileInputPolicy
import com.example.campusmate.domain.ai.file.AiFileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiFileInputPolicyTest {
    @Test
    fun resolveKind_acceptsOnlyDeclaredMimeTypesAndSafeExtensionFallbacks() {
        assertEquals(AiFileKind.TEXT, AiFileInputPolicy.resolveKind("text/plain", "notes.bin"))
        assertEquals(AiFileKind.IMAGE, AiFileInputPolicy.resolveKind("image/png", "image.bin"))
        assertEquals(AiFileKind.PDF, AiFileInputPolicy.resolveKind("application/pdf", "paper.bin"))
        assertNull(AiFileInputPolicy.resolveKind("text/calendar", "calendar.txt"))
        assertNull(AiFileInputPolicy.resolveKind("text/vcard", "contact.txt"))
        assertEquals(AiFileKind.TEXT, AiFileInputPolicy.resolveKind(null, "notes.md"))
        assertEquals(
            AiFileKind.IMAGE,
            AiFileInputPolicy.resolveKind("application/octet-stream", "photo.webp")
        )
    }

    @Test
    fun normalizedMimeType_canonicalizesAliasesAndGenericFallbacks() {
        assertEquals(
            "image/jpeg",
            AiFileInputPolicy.normalizedMimeType(AiFileKind.IMAGE, "image/jpg", "photo.jpg")
        )
        assertEquals(
            "application/pdf",
            AiFileInputPolicy.normalizedMimeType(
                AiFileKind.PDF,
                "application/octet-stream",
                "paper.pdf"
            )
        )
        assertEquals(
            "application/json",
            AiFileInputPolicy.normalizedMimeType(AiFileKind.TEXT, null, "context.json")
        )
    }

    @Test
    fun validate_enforcesSizeAndUserDeclaredCapability() {
        assertNull(
            AiFileInputPolicy.validate(
                AiFileKind.TEXT,
                AiFileInputPolicy.MAX_TEXT_BYTES.toLong(),
                LlmMultimodalCapability.TEXT_ONLY
            )
        )
        assertEquals(
            AiFileInputError.TEXT_TOO_LARGE,
            AiFileInputPolicy.validate(
                AiFileKind.TEXT,
                AiFileInputPolicy.MAX_TEXT_BYTES + 1L,
                LlmMultimodalCapability.TEXT_ONLY
            )
        )
        assertEquals(
            AiFileInputError.CAPABILITY_MISMATCH,
            AiFileInputPolicy.validate(
                AiFileKind.IMAGE,
                1L,
                LlmMultimodalCapability.TEXT_ONLY
            )
        )
        assertNull(
            AiFileInputPolicy.validate(
                AiFileKind.IMAGE,
                1L,
                LlmMultimodalCapability.IMAGE_INPUT
            )
        )
        assertEquals(
            AiFileInputError.CAPABILITY_MISMATCH,
            AiFileInputPolicy.validate(
                AiFileKind.PDF,
                1L,
                LlmMultimodalCapability.IMAGE_INPUT
            )
        )
        assertNull(
            AiFileInputPolicy.validate(
                AiFileKind.PDF,
                1L,
                LlmMultimodalCapability.IMAGE_AND_FILE_INPUT
            )
        )
    }
}
