package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.FocusSession
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getNullableLong
import com.example.campusmate.util.DbUtils.getRequiredInt
import com.example.campusmate.util.DbUtils.getRequiredLong

/** Repository for focus session persistence; service wiring comes later. */
class FocusRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun addFocusSession(session: FocusSession): Long {
        require(session.plannedDurationSec > 0) { "Planned focus duration must be positive." }
        val uri = resolver.insert(
            CampusMateContract.FocusSessions.CONTENT_URI,
            session.toContentValues(createdAt = DateTimeUtils.nowMillis())
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }

    fun updateFocusSession(session: FocusSession): Boolean {
        require(session.id > 0L) { "Focus session id is required for update." }
        val rows = resolver.update(
            CampusMateContract.FocusSessions.buildItemUri(session.id),
            session.toContentValues(),
            null,
            null
        )
        return rows > 0
    }

    fun getFocusSessionById(sessionId: Long): FocusSession? {
        val sessions = mutableListOf<FocusSession>()
        resolver.query(CampusMateContract.FocusSessions.buildItemUri(sessionId), null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                sessions.add(
                    FocusSession(
                        id = cursor.getRequiredLong(BaseColumns._ID),
                        taskId = cursor.getNullableLong(CampusMateContract.FocusSessions.COLUMN_TASK_ID),
                        courseId = cursor.getNullableLong(CampusMateContract.FocusSessions.COLUMN_COURSE_ID),
                        plannedDurationSec = cursor.getRequiredInt(CampusMateContract.FocusSessions.COLUMN_PLANNED_DURATION_SEC),
                        actualDurationSec = cursor.getRequiredInt(CampusMateContract.FocusSessions.COLUMN_ACTUAL_DURATION_SEC),
                        startAt = cursor.getNullableLong(CampusMateContract.FocusSessions.COLUMN_START_AT),
                        endAt = cursor.getNullableLong(CampusMateContract.FocusSessions.COLUMN_END_AT),
                        status = cursor.getRequiredInt(CampusMateContract.FocusSessions.COLUMN_STATUS),
                        pauseCount = cursor.getRequiredInt(CampusMateContract.FocusSessions.COLUMN_PAUSE_COUNT),
                        interruptCount = cursor.getRequiredInt(CampusMateContract.FocusSessions.COLUMN_INTERRUPT_COUNT),
                        createdAt = cursor.getRequiredLong(CampusMateContract.FocusSessions.COLUMN_CREATED_AT)
                    )
                )
            }
        }
        return sessions.firstOrNull()
    }

    private fun FocusSession.toContentValues(createdAt: Long? = null): ContentValues {
        return ContentValues().apply {
            if (taskId == null) putNull(CampusMateContract.FocusSessions.COLUMN_TASK_ID) else put(CampusMateContract.FocusSessions.COLUMN_TASK_ID, taskId)
            if (courseId == null) putNull(CampusMateContract.FocusSessions.COLUMN_COURSE_ID) else put(CampusMateContract.FocusSessions.COLUMN_COURSE_ID, courseId)
            put(CampusMateContract.FocusSessions.COLUMN_PLANNED_DURATION_SEC, plannedDurationSec)
            put(CampusMateContract.FocusSessions.COLUMN_ACTUAL_DURATION_SEC, actualDurationSec)
            if (startAt == null) putNull(CampusMateContract.FocusSessions.COLUMN_START_AT) else put(CampusMateContract.FocusSessions.COLUMN_START_AT, startAt)
            if (endAt == null) putNull(CampusMateContract.FocusSessions.COLUMN_END_AT) else put(CampusMateContract.FocusSessions.COLUMN_END_AT, endAt)
            put(CampusMateContract.FocusSessions.COLUMN_STATUS, status)
            put(CampusMateContract.FocusSessions.COLUMN_PAUSE_COUNT, pauseCount)
            put(CampusMateContract.FocusSessions.COLUMN_INTERRUPT_COUNT, interruptCount)
            createdAt?.let { put(CampusMateContract.FocusSessions.COLUMN_CREATED_AT, it) }
        }
    }
}
