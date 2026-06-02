package com.example.campusmate.data.model

/** Task, homework, exam, or review item associated with a course. */
data class StudyTask(
    val id: Long = 0L,
    val courseId: Long? = null,
    val title: String,
    val description: String? = null,
    val type: Int = TYPE_HOMEWORK,
    val priority: Int = PRIORITY_NORMAL,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val status: Int = STATUS_TODO,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    companion object {
        const val TYPE_HOMEWORK = 0
        const val TYPE_EXPERIMENT = 1
        const val TYPE_EXAM = 2
        const val TYPE_REVIEW = 3
        const val TYPE_PROJECT = 4
        const val TYPE_OTHER = 5

        const val PRIORITY_LOW = 0
        const val PRIORITY_MEDIUM = 1
        const val PRIORITY_NORMAL = 1
        const val PRIORITY_HIGH = 2

        const val STATUS_TODO = 0
        const val STATUS_DONE = 1
        const val STATUS_ARCHIVED = 2
    }
}
