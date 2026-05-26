package com.example.campusmate.domain.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.data.repository.UserProfileRepository
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Extracts CampusMate public profile JSON from NFC MIME or text records. */
class NfcPayloadParser(
    private val userProfileRepository: UserProfileRepository
) {
    fun parse(message: NdefMessage): NfcCardPayload {
        val json = extractPublicProfileJson(message)
        val buddy = userProfileRepository.parsePublicProfileJson(json, StudyBuddy.SOURCE_NFC)
        return NfcCardPayload(rawJson = json, buddy = buddy)
    }

    fun extractPublicProfileJson(message: NdefMessage): String {
        return message.records.firstNotNullOfOrNull(::recordToJson)
            ?: throw IllegalArgumentException("No CampusMate profile record found.")
    }

    private fun recordToJson(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA &&
            String(record.type, StandardCharsets.US_ASCII).lowercase(Locale.US) == NfcPayloadWriter.MIME_TYPE
        ) {
            return String(record.payload, StandardCharsets.UTF_8)
        }

        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            return parseTextRecord(record)
        }
        return null
    }

    private fun parseTextRecord(record: NdefRecord): String {
        val payload = record.payload
        require(payload.isNotEmpty()) { "Empty NFC text payload." }
        val status = payload[0].toInt()
        val isUtf16 = status and 0x80 != 0
        val languageCodeLength = status and 0x3F
        val textStart = 1 + languageCodeLength
        require(payload.size >= textStart) { "Invalid NFC text payload." }
        val charset: Charset = if (isUtf16) StandardCharsets.UTF_16 else StandardCharsets.UTF_8
        return String(payload, textStart, payload.size - textStart, charset)
    }
}
