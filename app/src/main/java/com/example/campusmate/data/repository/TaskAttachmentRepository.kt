package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.TaskAttachment
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getNullableString
import com.example.campusmate.util.DbUtils.getNullableLong
import com.example.campusmate.util.DbUtils.getRequiredLong

/** Repository for task attachments, scoped behind ContentResolver/Provider. */
class TaskAttachmentRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun addAttachment(taskId: Long, uri: String, mimeType: String?, title: String?): Long {
        require(taskId > 0L) { "Task id is required." }
        require(uri.isNotBlank()) { "Attachment uri cannot be blank." }
        val now = DateTimeUtils.nowMillis()
        val inserted = resolver.insert(
            CampusMateContract.TaskAttachments.CONTENT_URI,
            ContentValues().apply {
                put(CampusMateContract.TaskAttachments.COLUMN_TASK_ID, taskId)
                put(CampusMateContract.TaskAttachments.COLUMN_URI, uri)
                put(CampusMateContract.TaskAttachments.COLUMN_MIME_TYPE, mimeType)
                put(CampusMateContract.TaskAttachments.COLUMN_TITLE, title)
                put(CampusMateContract.TaskAttachments.COLUMN_CREATED_AT, now)
            }
        )
        return inserted?.let(ContentUris::parseId) ?: -1L
    }

    fun getAttachmentsByTask(taskId: Long): List<TaskAttachment> {
        require(taskId > 0L) { "Task id is required." }
        val items = mutableListOf<TaskAttachment>()
        resolver.query(
            CampusMateContract.TaskAttachments.CONTENT_URI,
            null,
            "${CampusMateContract.TaskAttachments.COLUMN_TASK_ID}=?",
            arrayOf(taskId.toString()),
            "${CampusMateContract.TaskAttachments.COLUMN_CREATED_AT} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    TaskAttachment(
                        id = cursor.getRequiredLong(BaseColumns._ID),
                        taskId = cursor.getRequiredLong(CampusMateContract.TaskAttachments.COLUMN_TASK_ID),
                        uri = cursor.getNullableString(CampusMateContract.TaskAttachments.COLUMN_URI).orEmpty(),
                        mimeType = cursor.getNullableString(CampusMateContract.TaskAttachments.COLUMN_MIME_TYPE),
                        title = cursor.getNullableString(CampusMateContract.TaskAttachments.COLUMN_TITLE),
                        createdAt = cursor.getNullableLong(CampusMateContract.TaskAttachments.COLUMN_CREATED_AT) ?: 0L
                    )
                )
            }
        }
        return items
    }

    fun deleteAttachment(attachmentId: Long): Boolean {
        require(attachmentId > 0L) { "Attachment id is required." }
        val rows = resolver.delete(CampusMateContract.TaskAttachments.buildItemUri(attachmentId), null, null)
        return rows > 0
    }

    fun deleteAttachmentsByTask(taskId: Long): Int {
        require(taskId > 0L) { "Task id is required." }
        return resolver.delete(
            CampusMateContract.TaskAttachments.CONTENT_URI,
            "${CampusMateContract.TaskAttachments.COLUMN_TASK_ID}=?",
            arrayOf(taskId.toString())
        )
    }
}
