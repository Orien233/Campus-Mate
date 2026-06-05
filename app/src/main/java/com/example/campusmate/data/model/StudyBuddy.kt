package com.example.campusmate.data.model

/** A study partner imported from QR or future manual entry. */
data class StudyBuddy(
    val id: Long = 0L,
    val nickname: String,
    val school: String? = null,
    val major: String? = null,
    val grade: String? = null,
    val bio: String? = null,
    val github: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val source: Int = SOURCE_QR,
    val addedAt: Long = 0L,
    val note: String? = null
) {
    companion object {
        const val SOURCE_QR = 0
        const val SOURCE_LEGACY_REMOVED = 1
        const val SOURCE_MANUAL = 2
    }
}
