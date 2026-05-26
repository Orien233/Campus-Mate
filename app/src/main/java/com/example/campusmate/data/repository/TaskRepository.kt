package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.StudyTask
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getBooleanFlag
import com.example.campusmate.util.DbUtils.getNullableLong
import com.example.campusmate.util.DbUtils.getNullableString
import com.example.campusmate.util.DbUtils.getRequiredInt
import com.example.campusmate.util.DbUtils.getRequiredLong
import com.example.campusmate.util.DbUtils.getRequiredString

/** Repository for task CRUD, status changes, and due-date queries. */
class TaskRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun addTask(task: StudyTask): Long {
        validateTask(task)
        val now = DateTimeUtils.nowMillis()
        val uri = resolver.insert(
            CampusMateContract.StudyTasks.CONTENT_URI,
            task.toContentValues(createdAt = now, updatedAt = now)
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }

    fun updateTask(task: StudyTask): Boolean {
        require(task.id > 0L) { "Task id is required for update." }
        validateTask(task)
        val rows = resolver.update(
            CampusMateContract.StudyTasks.buildItemUri(task.id),
            task.toContentValues(updatedAt = DateTimeUtils.nowMillis()),
            null,
            null
        )
        return rows > 0
    }

    fun markDone(taskId: Long): Boolean = updateTaskStatus(taskId, StudyTask.STATUS_DONE)

    fun markTodo(taskId: Long): Boolean = updateTaskStatus(taskId, StudyTask.STATUS_TODO)

    fun deleteTask(taskId: Long): Boolean {
        require(taskId > 0L) { "Task id is required for delete." }
        val rows = resolver.update(
            CampusMateContract.StudyTasks.buildItemUri(taskId),
            ContentValues().apply {
                put(CampusMateContract.StudyTasks.COLUMN_IS_DELETED, 1)
                put(CampusMateContract.StudyTasks.COLUMN_UPDATED_AT, DateTimeUtils.nowMillis())
            },
            null,
            null
        )
        return rows > 0
    }

    fun getTaskById(taskId: Long): StudyTask? {
        return queryTasks(
            uri = CampusMateContract.StudyTasks.buildItemUri(taskId),
            selection = "${CampusMateContract.StudyTasks.COLUMN_IS_DELETED}=?",
            selectionArgs = arrayOf("0")
        ).firstOrNull()
    }

    fun getAllTasks(): List<StudyTask> {
        return queryTasks(
            selection = "${CampusMateContract.StudyTasks.COLUMN_IS_DELETED}=?",
            selectionArgs = arrayOf("0"),
            sortOrder = "${CampusMateContract.StudyTasks.COLUMN_STATUS} ASC, ${CampusMateContract.StudyTasks.COLUMN_DUE_AT} ASC"
        )
    }

    fun getTasksByCourse(courseId: Long): List<StudyTask> {
        return queryTasks(
            selection = "${CampusMateContract.StudyTasks.COLUMN_IS_DELETED}=? AND ${CampusMateContract.StudyTasks.COLUMN_COURSE_ID}=?",
            selectionArgs = arrayOf("0", courseId.toString()),
            sortOrder = "${CampusMateContract.StudyTasks.COLUMN_DUE_AT} ASC"
        )
    }

    fun getTodayTasks(): List<StudyTask> {
        return queryTasks(
            selection = "${CampusMateContract.StudyTasks.COLUMN_IS_DELETED}=? AND ${CampusMateContract.StudyTasks.COLUMN_DUE_AT} BETWEEN ? AND ?",
            selectionArgs = arrayOf(
                "0",
                DateTimeUtils.startOfTodayMillis().toString(),
                DateTimeUtils.endOfTodayMillis().toString()
            ),
            sortOrder = "${CampusMateContract.StudyTasks.COLUMN_DUE_AT} ASC"
        )
    }

    fun getUpcomingTasks(limit: Int): List<StudyTask> {
        val safeLimit = limit.coerceAtLeast(1)
        return queryTasks(
            selection = "${CampusMateContract.StudyTasks.COLUMN_IS_DELETED}=? AND ${CampusMateContract.StudyTasks.COLUMN_STATUS}=? AND ${CampusMateContract.StudyTasks.COLUMN_DUE_AT}>=?",
            selectionArgs = arrayOf("0", StudyTask.STATUS_TODO.toString(), DateTimeUtils.nowMillis().toString()),
            sortOrder = "${CampusMateContract.StudyTasks.COLUMN_DUE_AT} ASC LIMIT $safeLimit"
        )
    }

    fun getOverdueTasks(): List<StudyTask> {
        return queryTasks(
            selection = "${CampusMateContract.StudyTasks.COLUMN_IS_DELETED}=? AND ${CampusMateContract.StudyTasks.COLUMN_STATUS}=? AND ${CampusMateContract.StudyTasks.COLUMN_DUE_AT}<?",
            selectionArgs = arrayOf("0", StudyTask.STATUS_TODO.toString(), DateTimeUtils.nowMillis().toString()),
            sortOrder = "${CampusMateContract.StudyTasks.COLUMN_DUE_AT} ASC"
        )
    }

    private fun updateTaskStatus(taskId: Long, status: Int): Boolean {
        require(taskId > 0L) { "Task id is required for status update." }
        val rows = resolver.update(
            CampusMateContract.StudyTasks.buildItemUri(taskId),
            ContentValues().apply {
                put(CampusMateContract.StudyTasks.COLUMN_STATUS, status)
                put(CampusMateContract.StudyTasks.COLUMN_UPDATED_AT, DateTimeUtils.nowMillis())
            },
            null,
            null
        )
        return rows > 0
    }

    private fun queryTasks(
        uri: android.net.Uri = CampusMateContract.StudyTasks.CONTENT_URI,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<StudyTask> {
        val tasks = mutableListOf<StudyTask>()
        resolver.query(uri, null, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                tasks.add(
                    StudyTask(
                        id = cursor.getRequiredLong(BaseColumns._ID),
                        courseId = cursor.getNullableLong(CampusMateContract.StudyTasks.COLUMN_COURSE_ID),
                        title = cursor.getRequiredString(CampusMateContract.StudyTasks.COLUMN_TITLE),
                        description = cursor.getNullableString(CampusMateContract.StudyTasks.COLUMN_DESCRIPTION),
                        type = cursor.getRequiredInt(CampusMateContract.StudyTasks.COLUMN_TYPE),
                        priority = cursor.getRequiredInt(CampusMateContract.StudyTasks.COLUMN_PRIORITY),
                        dueAt = cursor.getNullableLong(CampusMateContract.StudyTasks.COLUMN_DUE_AT),
                        remindAt = cursor.getNullableLong(CampusMateContract.StudyTasks.COLUMN_REMIND_AT),
                        status = cursor.getRequiredInt(CampusMateContract.StudyTasks.COLUMN_STATUS),
                        createdAt = cursor.getRequiredLong(CampusMateContract.StudyTasks.COLUMN_CREATED_AT),
                        updatedAt = cursor.getRequiredLong(CampusMateContract.StudyTasks.COLUMN_UPDATED_AT),
                        isDeleted = cursor.getBooleanFlag(CampusMateContract.StudyTasks.COLUMN_IS_DELETED)
                    )
                )
            }
        }
        return tasks
    }

    private fun StudyTask.toContentValues(createdAt: Long? = null, updatedAt: Long? = null): ContentValues {
        return ContentValues().apply {
            if (courseId == null) putNull(CampusMateContract.StudyTasks.COLUMN_COURSE_ID) else put(CampusMateContract.StudyTasks.COLUMN_COURSE_ID, courseId)
            put(CampusMateContract.StudyTasks.COLUMN_TITLE, title.trim())
            put(CampusMateContract.StudyTasks.COLUMN_DESCRIPTION, description)
            put(CampusMateContract.StudyTasks.COLUMN_TYPE, type)
            put(CampusMateContract.StudyTasks.COLUMN_PRIORITY, priority)
            if (dueAt == null) putNull(CampusMateContract.StudyTasks.COLUMN_DUE_AT) else put(CampusMateContract.StudyTasks.COLUMN_DUE_AT, dueAt)
            if (remindAt == null) putNull(CampusMateContract.StudyTasks.COLUMN_REMIND_AT) else put(CampusMateContract.StudyTasks.COLUMN_REMIND_AT, remindAt)
            put(CampusMateContract.StudyTasks.COLUMN_STATUS, status)
            put(CampusMateContract.StudyTasks.COLUMN_IS_DELETED, if (isDeleted) 1 else 0)
            createdAt?.let { put(CampusMateContract.StudyTasks.COLUMN_CREATED_AT, it) }
            updatedAt?.let { put(CampusMateContract.StudyTasks.COLUMN_UPDATED_AT, it) }
        }
    }

    private fun validateTask(task: StudyTask) {
        require(task.title.isNotBlank()) { "Task title cannot be blank." }
        require(task.type in 0..5) { "Task type must be 0..5." }
        require(task.priority in 0..2) { "Task priority must be 0..2." }
        require(task.status in 0..2) { "Task status must be 0..2." }
    }
}
