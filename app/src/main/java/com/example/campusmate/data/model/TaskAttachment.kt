package com.example.campusmate.data.model

/** One task attachment item, typically an image Uri picked from SAF. */
data class TaskAttachment(
    val id: Long = 0L,
    val taskId: Long,
    val uri: String,
    val mimeType: String? = null,
    val title: String? = null,
    val createdAt: Long = 0L
)

