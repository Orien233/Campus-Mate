package com.example.campusmate.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.StudyPlan
import com.example.campusmate.util.DateTimeUtils

class StudyPlanRepository(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    fun addPlan(plan: StudyPlan): Long {
        val now = DateTimeUtils.nowMillis()
        val uri = resolver.insert(
            CampusMateContract.StudyPlans.CONTENT_URI,
            plan.toContentValues(createdAt = now, updatedAt = now)
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }

    fun addPlans(plans: List<StudyPlan>): Int {
        val values = plans.map { plan ->
            val now = DateTimeUtils.nowMillis()
            plan.toContentValues(createdAt = now, updatedAt = now)
        }.toTypedArray()
        return resolver.bulkInsert(CampusMateContract.StudyPlans.CONTENT_URI, values)
    }

    fun getPlanById(planId: Long): StudyPlan? {
        val uri = CampusMateContract.StudyPlans.buildItemUri(planId)
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return StudyPlan.fromCursor(cursor)
            }
        }
        return null
    }

    fun getAllPlans(): List<StudyPlan> {
        val plans = mutableListOf<StudyPlan>()
        resolver.query(
            CampusMateContract.StudyPlans.CONTENT_URI,
            null,
            null,
            null,
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                plans.add(StudyPlan.fromCursor(cursor))
            }
        }
        return plans
    }

    fun getPlansByDate(date: String): List<StudyPlan> {
        val plans = mutableListOf<StudyPlan>()
        resolver.query(
            CampusMateContract.StudyPlans.CONTENT_URI,
            null,
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} = ?",
            arrayOf(date),
            "${CampusMateContract.StudyPlans.COLUMN_START_TIME} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                plans.add(StudyPlan.fromCursor(cursor))
            }
        }
        return plans
    }

    fun getActivePlans(): List<StudyPlan> {
        val plans = mutableListOf<StudyPlan>()
        val today = DateTimeUtils.formatDate(DateTimeUtils.nowMillis())
        resolver.query(
            CampusMateContract.StudyPlans.CONTENT_URI,
            null,
            "${CampusMateContract.StudyPlans.COLUMN_STATUS} = ? AND ${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} >= ?",
            arrayOf(StudyPlan.STATUS_PENDING.toString(), today),
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} ASC, ${CampusMateContract.StudyPlans.COLUMN_START_TIME} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                plans.add(StudyPlan.fromCursor(cursor))
            }
        }
        return plans
    }

    fun getWeeklyPlans(startDate: String, endDate: String): List<StudyPlan> {
        val plans = mutableListOf<StudyPlan>()
        resolver.query(
            CampusMateContract.StudyPlans.CONTENT_URI,
            null,
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} >= ? AND ${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} <= ?",
            arrayOf(startDate, endDate),
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} ASC, ${CampusMateContract.StudyPlans.COLUMN_START_TIME} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                plans.add(StudyPlan.fromCursor(cursor))
            }
        }
        return plans
    }

    fun updatePlan(plan: StudyPlan): Boolean {
        val uri = CampusMateContract.StudyPlans.buildItemUri(plan.id)
        val rows = resolver.update(uri, plan.toContentValues(updatedAt = DateTimeUtils.nowMillis()), null, null)
        return rows > 0
    }

    fun updatePlanStatus(planId: Long, status: Int): Boolean {
        val uri = CampusMateContract.StudyPlans.buildItemUri(planId)
        val values = ContentValues().apply {
            put(CampusMateContract.StudyPlans.COLUMN_STATUS, status)
            put(CampusMateContract.StudyPlans.COLUMN_UPDATED_AT, DateTimeUtils.nowMillis())
        }
        val rows = resolver.update(uri, values, null, null)
        return rows > 0
    }

    fun updatePlanActualMinutes(planId: Long, actualMinutes: Int): Boolean {
        val uri = CampusMateContract.StudyPlans.buildItemUri(planId)
        val values = ContentValues().apply {
            put(CampusMateContract.StudyPlans.COLUMN_ACTUAL_MINUTES, actualMinutes)
            put(CampusMateContract.StudyPlans.COLUMN_UPDATED_AT, DateTimeUtils.nowMillis())
        }
        val rows = resolver.update(uri, values, null, null)
        return rows > 0
    }

    fun deletePlan(planId: Long): Boolean {
        val uri = CampusMateContract.StudyPlans.buildItemUri(planId)
        val rows = resolver.delete(uri, null, null)
        return rows > 0
    }

    fun deletePlansByDate(date: String): Int {
        return resolver.delete(
            CampusMateContract.StudyPlans.CONTENT_URI,
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} = ?",
            arrayOf(date)
        )
    }

    fun deletePlansOverlapping(plans: List<StudyPlan>): Int {
        var deleted = 0
        for ((date, dayPlans) in plans.groupBy { it.planDate }) {
            val newBlocks = dayPlans.mapNotNull { it.timeBlock() }
            if (newBlocks.isEmpty()) continue
            for (existing in getPlansByDate(date)) {
                val existingBlock = existing.timeBlock() ?: continue
                if (newBlocks.any { it.overlaps(existingBlock) } && deletePlan(existing.id)) {
                    deleted += 1
                }
            }
        }
        return deleted
    }

    fun hasPlanForDate(date: String): Boolean {
        resolver.query(
            CampusMateContract.StudyPlans.CONTENT_URI,
            arrayOf(BaseColumns._ID),
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} = ?",
            arrayOf(date),
            null
        )?.use { cursor ->
            return cursor.count > 0
        }
        return false
    }

    fun getTotalPlannedMinutes(date: String): Int {
        var total = 0
        resolver.query(
            CampusMateContract.StudyPlans.CONTENT_URI,
            arrayOf(CampusMateContract.StudyPlans.COLUMN_PLANNED_MINUTES),
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} = ?",
            arrayOf(date),
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_PLANNED_MINUTES)
            while (cursor.moveToNext()) {
                total += cursor.getInt(index)
            }
        }
        return total
    }

    fun getTotalActualMinutes(date: String): Int {
        var total = 0
        resolver.query(
            CampusMateContract.StudyPlans.CONTENT_URI,
            arrayOf(CampusMateContract.StudyPlans.COLUMN_ACTUAL_MINUTES),
            "${CampusMateContract.StudyPlans.COLUMN_PLAN_DATE} = ?",
            arrayOf(date),
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(CampusMateContract.StudyPlans.COLUMN_ACTUAL_MINUTES)
            while (cursor.moveToNext()) {
                total += cursor.getInt(index)
            }
        }
        return total
    }

    private fun StudyPlan.timeBlock(): Pair<Int, Int>? {
        val start = parseTime(startTime) ?: return null
        val end = parseTime(endTime) ?: return null
        return (start to end).takeIf { it.second > it.first }
    }

    private fun Pair<Int, Int>.overlaps(other: Pair<Int, Int>): Boolean {
        return first < other.second && second > other.first
    }

    private fun parseTime(value: String?): Int? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(value?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }
}
