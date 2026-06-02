package com.example.campusmate.data.model

import android.content.ContentValues
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.util.DateTimeUtils

data class StudyPlan(
    val id: Long = 0L,
    val title: String,
    val planDate: String,
    val plannedMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    val type: Int = TYPE_DAILY,
    val status: Int = STATUS_PENDING,
    val sourceType: Int = SOURCE_AUTO,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toContentValues(createdAt: Long = this.createdAt, updatedAt: Long = this.updatedAt): ContentValues {
        return ContentValues().apply {
            if (id > 0L) put(BaseColumns._ID, id)
            put(CampusMateContract.StudyPlans.COLUMN_TITLE, title)
            put(CampusMateContract.StudyPlans.COLUMN_PLAN_DATE, planDate)
            put(CampusMateContract.StudyPlans.COLUMN_PLANNED_MINUTES, plannedMinutes)
            put(CampusMateContract.StudyPlans.COLUMN_ACTUAL_MINUTES, actualMinutes)
            put(CampusMateContract.StudyPlans.COLUMN_START_TIME, startTime)
            put(CampusMateContract.StudyPlans.COLUMN_END_TIME, endTime)
            put(CampusMateContract.StudyPlans.COLUMN_TYPE, type)
            put(CampusMateContract.StudyPlans.COLUMN_STATUS, status)
            put(CampusMateContract.StudyPlans.COLUMN_SOURCE_TYPE, sourceType)
            put(CampusMateContract.StudyPlans.COLUMN_CREATED_AT, createdAt)
            put(CampusMateContract.StudyPlans.COLUMN_UPDATED_AT, updatedAt)
        }
    }

    companion object {
        const val TYPE_DAILY = CampusMateContract.StudyPlans.TYPE_DAILY
        const val TYPE_WEEKLY = CampusMateContract.StudyPlans.TYPE_WEEKLY

        const val STATUS_PENDING = CampusMateContract.StudyPlans.STATUS_PENDING
        const val STATUS_COMPLETED = CampusMateContract.StudyPlans.STATUS_COMPLETED
        const val STATUS_EXPIRED = CampusMateContract.StudyPlans.STATUS_EXPIRED

        const val SOURCE_MANUAL = CampusMateContract.StudyPlans.SOURCE_MANUAL
        const val SOURCE_AUTO = CampusMateContract.StudyPlans.SOURCE_AUTO
        const val SOURCE_LLM = CampusMateContract.StudyPlans.SOURCE_LLM

        fun fromCursor(cursor: android.database.Cursor): StudyPlan {
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_TITLE)
            val planDateIndex = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_PLAN_DATE)
            val plannedMinutesIndex = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_PLANNED_MINUTES)
            val actualMinutesIndex = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_ACTUAL_MINUTES)
            val startTimeIndex = cursor.getColumnIndex(CampusMateContract.StudyPlans.COLUMN_START_TIME)
            val endTimeIndex = cursor.getColumnIndex(CampusMateContract.StudyPlans.COLUMN_END_TIME)
            val typeIndex = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_TYPE)
            val statusIndex = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_STATUS)
            val sourceTypeIndex = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_SOURCE_TYPE)
            val createdAtIndex = cursor.getColumnIndex(CampusMateContract.StudyPlans.COLUMN_CREATED_AT)
            val updatedAtIndex = cursor.getColumnIndex(CampusMateContract.StudyPlans.COLUMN_UPDATED_AT)

            return StudyPlan(
                id = cursor.getLong(idIndex),
                title = cursor.getString(titleIndex) ?: "",
                planDate = cursor.getString(planDateIndex) ?: "",
                plannedMinutes = cursor.getInt(plannedMinutesIndex),
                actualMinutes = cursor.getInt(actualMinutesIndex),
                startTime = if (startTimeIndex >= 0) cursor.getString(startTimeIndex) else null,
                endTime = if (endTimeIndex >= 0) cursor.getString(endTimeIndex) else null,
                type = cursor.getInt(typeIndex),
                status = cursor.getInt(statusIndex),
                sourceType = cursor.getInt(sourceTypeIndex),
                createdAt = if (createdAtIndex >= 0) cursor.getLong(createdAtIndex) else 0L,
                updatedAt = if (updatedAtIndex >= 0) cursor.getLong(updatedAtIndex) else 0L
            )
        }
    }
}
