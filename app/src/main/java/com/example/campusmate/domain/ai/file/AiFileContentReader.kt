package com.example.campusmate.domain.ai.file

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.campusmate.data.model.llm.LlmMultimodalCapability
import com.example.campusmate.domain.llm.LlmInlineData
import java.io.ByteArrayOutputStream

class AiFileContentReader(
    private val contentResolver: ContentResolver
) {
    fun inspect(uri: Uri): AiSelectedFile {
        return try {
            var displayName = ""
            var sizeBytes: Long? = null
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex)?.trim().orEmpty()
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex).takeIf { it >= 0L }
                    }
                }
            }
            val reportedMimeType = contentResolver.getType(uri)
            val kind = AiFileInputPolicy.resolveKind(reportedMimeType, displayName)
                ?: throw AiFileReadException(AiFileInputError.UNSUPPORTED_TYPE)
            AiSelectedFile(
                displayName = displayName,
                mimeType = AiFileInputPolicy.normalizedMimeType(kind, reportedMimeType, displayName),
                sizeBytes = sizeBytes
            )
        } catch (error: AiFileReadException) {
            throw error
        } catch (error: Exception) {
            throw AiFileReadException(AiFileInputError.READ_FAILED, error)
        }
    }

    fun read(
        uri: Uri,
        selectedFile: AiSelectedFile,
        capability: LlmMultimodalCapability
    ): AiFileInput {
        val kind = AiFileInputPolicy.resolveKind(selectedFile.mimeType, selectedFile.displayName)
            ?: throw AiFileReadException(AiFileInputError.UNSUPPORTED_TYPE)
        AiFileInputPolicy.validate(kind, selectedFile.sizeBytes, capability)?.let {
            throw AiFileReadException(it)
        }
        val limit = if (kind == AiFileKind.TEXT) {
            AiFileInputPolicy.MAX_TEXT_BYTES
        } else {
            AiFileInputPolicy.MAX_BINARY_BYTES
        }
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { input ->
                input.readBounded(limit)
            } ?: throw AiFileReadException(AiFileInputError.READ_FAILED)
        } catch (error: AiFileReadException) {
            throw error
        } catch (error: Exception) {
            throw AiFileReadException(AiFileInputError.READ_FAILED, error)
        }
        if (bytes.isEmpty()) throw AiFileReadException(AiFileInputError.EMPTY_FILE)

        return when (kind) {
            AiFileKind.TEXT -> {
                val content = bytes.toString(Charsets.UTF_8)
                if (content.isBlank()) {
                    throw AiFileReadException(AiFileInputError.EMPTY_FILE)
                }
                if (content.length > AiFileInputPolicy.MAX_TEXT_CHARS) {
                    throw AiFileReadException(AiFileInputError.TEXT_TOO_LONG)
                }
                AiFileInput.Text(
                    mimeType = selectedFile.mimeType,
                    sizeBytes = bytes.size.toLong(),
                    content = content
                )
            }
            AiFileKind.IMAGE,
            AiFileKind.PDF -> AiFileInput.Inline(
                data = LlmInlineData(selectedFile.mimeType, bytes)
            )
        }
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw AiFileReadException(
                    if (maxBytes == AiFileInputPolicy.MAX_TEXT_BYTES) {
                        AiFileInputError.TEXT_TOO_LARGE
                    } else {
                        AiFileInputError.BINARY_TOO_LARGE
                    }
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
