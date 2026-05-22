package com.example.campusmate.domain.nfc

import com.example.campusmate.data.model.StudyBuddy

/** CampusMate study-card data parsed from an NFC NDEF message. */
data class NfcCardPayload(
    val rawJson: String,
    val buddy: StudyBuddy
)
