package com.example.campusmate.data.model

/** Persisted lifecycle of one focus timer session. */
data class FocusSession(
    val id: Long = 0L,
    val taskId: Long? = null,
    val courseId: Long? = null,
    val plannedDurationSec: Int,
    val actualDurationSec: Int = 0,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val status: Int = STATUS_READY,
    val pauseCount: Int = 0,
    val interruptCount: Int = 0,
    val createdAt: Long = 0L
) {
    companion object {
        const val STATUS_READY = 0
        const val STATUS_RUNNING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_FINISHED = 3
        const val STATUS_CANCELLED = 4
    }
}
