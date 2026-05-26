package com.example.campusmate.data.model

/** Course persisted from manual entry or schedule import. */
data class Course(
    val id: Long = 0L,
    val name: String,
    val teacher: String? = null,
    val classroom: String? = null,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int = 1,
    val endWeek: Int = 18,
    val weekType: Int = WEEK_TYPE_EVERY,
    val color: String? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    companion object {
        const val WEEK_TYPE_EVERY = 0
        const val WEEK_TYPE_ODD = 1
        const val WEEK_TYPE_EVEN = 2
    }
}
