package com.example.campusmate.data.model

/** Final study record used by statistics and heatmap pages. */
data class StudyRecord(
    val id: Long = 0L,
    val taskId: Long? = null,
    val courseId: Long? = null,
    val focusSessionId: Long? = null,
    val title: String? = null,
    val durationSec: Int,
    val recordDate: String,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val source: Int = SOURCE_FOCUS_AUTO,
    val note: String? = null,
    val createdAt: Long = 0L
) {
    companion object {
        const val SOURCE_FOCUS_AUTO = 0
        const val SOURCE_MANUAL = 1
    }
}
