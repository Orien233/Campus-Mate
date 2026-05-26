package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.DailyStudyStat
import com.example.campusmate.data.model.StudyRecord
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getNullableLong
import com.example.campusmate.util.DbUtils.getNullableString
import com.example.campusmate.util.DbUtils.getRequiredInt
import com.example.campusmate.util.DbUtils.getRequiredLong
import com.example.campusmate.util.DbUtils.getRequiredString

/** Repository for study record writes and statistics-oriented reads. */
class StudyRecordRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun addStudyRecord(record: StudyRecord): Long {
        validateRecord(record)
        val uri = resolver.insert(
            CampusMateContract.StudyRecords.CONTENT_URI,
            record.toContentValues(createdAt = DateTimeUtils.nowMillis())
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }

    fun getRecordsByDate(date: String): List<StudyRecord> {
        return queryRecords(
            selection = "${CampusMateContract.StudyRecords.COLUMN_RECORD_DATE}=?",
            selectionArgs = arrayOf(date),
            sortOrder = "${CampusMateContract.StudyRecords.COLUMN_START_AT} ASC"
        )
    }

    fun getRecordsBetween(startDate: String, endDate: String): List<StudyRecord> {
        return queryRecords(
            selection = "${CampusMateContract.StudyRecords.COLUMN_RECORD_DATE}>=? AND ${CampusMateContract.StudyRecords.COLUMN_RECORD_DATE}<=?",
            selectionArgs = arrayOf(startDate, endDate),
            sortOrder = "${CampusMateContract.StudyRecords.COLUMN_RECORD_DATE} ASC, ${CampusMateContract.StudyRecords.COLUMN_START_AT} ASC"
        )
    }

    fun getDailyStats(startDate: String, endDate: String): List<DailyStudyStat> {
        return getRecordsBetween(startDate, endDate)
            .groupBy { it.recordDate }
            .toSortedMap()
            .map { (date, records) ->
                DailyStudyStat(
                    recordDate = date,
                    durationSec = records.sumOf { it.durationSec },
                    recordCount = records.size
                )
            }
    }

    fun getTodayDuration(): Int {
        return getRecordsByDate(DateTimeUtils.todayDate()).sumOf { it.durationSec }
    }

    fun getWeeklyDuration(): Int {
        return getRecordsBetween(DateTimeUtils.startOfWeekDate(), DateTimeUtils.todayDate())
            .sumOf { it.durationSec }
    }

    private fun queryRecords(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<StudyRecord> {
        val records = mutableListOf<StudyRecord>()
        resolver.query(CampusMateContract.StudyRecords.CONTENT_URI, null, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                records.add(
                    StudyRecord(
                        id = cursor.getRequiredLong(BaseColumns._ID),
                        taskId = cursor.getNullableLong(CampusMateContract.StudyRecords.COLUMN_TASK_ID),
                        courseId = cursor.getNullableLong(CampusMateContract.StudyRecords.COLUMN_COURSE_ID),
                        focusSessionId = cursor.getNullableLong(CampusMateContract.StudyRecords.COLUMN_FOCUS_SESSION_ID),
                        title = cursor.getNullableString(CampusMateContract.StudyRecords.COLUMN_TITLE),
                        durationSec = cursor.getRequiredInt(CampusMateContract.StudyRecords.COLUMN_DURATION_SEC),
                        recordDate = cursor.getRequiredString(CampusMateContract.StudyRecords.COLUMN_RECORD_DATE),
                        startAt = cursor.getNullableLong(CampusMateContract.StudyRecords.COLUMN_START_AT),
                        endAt = cursor.getNullableLong(CampusMateContract.StudyRecords.COLUMN_END_AT),
                        source = cursor.getRequiredInt(CampusMateContract.StudyRecords.COLUMN_SOURCE),
                        note = cursor.getNullableString(CampusMateContract.StudyRecords.COLUMN_NOTE),
                        createdAt = cursor.getRequiredLong(CampusMateContract.StudyRecords.COLUMN_CREATED_AT)
                    )
                )
            }
        }
        return records
    }

    private fun StudyRecord.toContentValues(createdAt: Long? = null): ContentValues {
        return ContentValues().apply {
            if (taskId == null) putNull(CampusMateContract.StudyRecords.COLUMN_TASK_ID) else put(CampusMateContract.StudyRecords.COLUMN_TASK_ID, taskId)
            if (courseId == null) putNull(CampusMateContract.StudyRecords.COLUMN_COURSE_ID) else put(CampusMateContract.StudyRecords.COLUMN_COURSE_ID, courseId)
            if (focusSessionId == null) putNull(CampusMateContract.StudyRecords.COLUMN_FOCUS_SESSION_ID) else put(CampusMateContract.StudyRecords.COLUMN_FOCUS_SESSION_ID, focusSessionId)
            put(CampusMateContract.StudyRecords.COLUMN_TITLE, title)
            put(CampusMateContract.StudyRecords.COLUMN_DURATION_SEC, durationSec)
            put(CampusMateContract.StudyRecords.COLUMN_RECORD_DATE, recordDate)
            if (startAt == null) putNull(CampusMateContract.StudyRecords.COLUMN_START_AT) else put(CampusMateContract.StudyRecords.COLUMN_START_AT, startAt)
            if (endAt == null) putNull(CampusMateContract.StudyRecords.COLUMN_END_AT) else put(CampusMateContract.StudyRecords.COLUMN_END_AT, endAt)
            put(CampusMateContract.StudyRecords.COLUMN_SOURCE, source)
            put(CampusMateContract.StudyRecords.COLUMN_NOTE, note)
            createdAt?.let { put(CampusMateContract.StudyRecords.COLUMN_CREATED_AT, it) }
        }
    }

    private fun validateRecord(record: StudyRecord) {
        require(record.durationSec > 0) { "Study record duration must be positive." }
        require(record.recordDate.isNotBlank()) { "Study record date cannot be blank." }
        require(record.source in 0..1) { "Study record source must be 0 or 1." }
    }
}
