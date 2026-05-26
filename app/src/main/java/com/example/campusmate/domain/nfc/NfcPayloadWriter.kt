package com.example.campusmate.domain.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Builds and writes NDEF payloads for CampusMate public profile JSON. */
object NfcPayloadWriter {
    const val MIME_TYPE = "application/vnd.campusmate.profile"

    fun createProfileMessage(publicProfileJson: String): NdefMessage {
        return NdefMessage(
            arrayOf(
                NdefRecord.createMime(
                    MIME_TYPE,
                    publicProfileJson.toByteArray(StandardCharsets.UTF_8)
                )
            )
        )
    }

    fun writeToTag(tag: Tag, message: NdefMessage): WriteResult {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return writeToExistingTag(ndef, message)
        }

        val formatable = NdefFormatable.get(tag) ?: return WriteResult.UNSUPPORTED_TAG
        return try {
            formatable.connect()
            formatable.format(message)
            WriteResult.SUCCESS
        } catch (_: IOException) {
            WriteResult.WRITE_FAILED
        } catch (_: FormatException) {
            WriteResult.WRITE_FAILED
        } finally {
            runCatching { formatable.close() }
        }
    }

    private fun writeToExistingTag(ndef: Ndef, message: NdefMessage): WriteResult {
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                return WriteResult.READ_ONLY_TAG
            }
            if (message.toByteArray().size > ndef.maxSize) {
                return WriteResult.MESSAGE_TOO_LARGE
            }
            ndef.writeNdefMessage(message)
            WriteResult.SUCCESS
        } catch (_: IOException) {
            WriteResult.WRITE_FAILED
        } catch (_: FormatException) {
            WriteResult.WRITE_FAILED
        } finally {
            runCatching { ndef.close() }
        }
    }

    enum class WriteResult {
        SUCCESS,
        READ_ONLY_TAG,
        MESSAGE_TOO_LARGE,
        UNSUPPORTED_TAG,
        WRITE_FAILED
    }
}
