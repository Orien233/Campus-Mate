package com.example.campusmate.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract.AiMemories
import com.example.campusmate.data.model.AiMemory
import com.example.campusmate.data.model.AiMemoryDraft
import com.example.campusmate.domain.ai.memory.AiMemoryDraftValidator
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getBooleanFlag
import com.example.campusmate.util.DbUtils.getNullableLong
import com.example.campusmate.util.DbUtils.getRequiredInt
import com.example.campusmate.util.DbUtils.getRequiredLong
import com.example.campusmate.util.DbUtils.getRequiredString

/** Local, user-managed text memory. No model service can write through an AI-result API. */
class AiMemoryRepository(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    fun addUserMemory(draft: AiMemoryDraft): Long {
        val normalized = AiMemoryDraftValidator.requireValid(draft)
        AiMemoryDraftValidator.requireCapacity(getMemoryCount())
        val now = DateTimeUtils.nowMillis()
        val values = normalized.toContentValues().apply {
            put(AiMemories.COLUMN_CREATED_AT, now)
            put(AiMemories.COLUMN_UPDATED_AT, now)
        }
        return resolver.insert(AiMemories.CONTENT_URI, values)?.let(ContentUris::parseId) ?: -1L
    }

    fun updateUserMemory(id: Long, draft: AiMemoryDraft): Boolean {
        require(id > 0L) { "Memory id is required." }
        val values = AiMemoryDraftValidator.requireValid(draft).toContentValues().apply {
            put(AiMemories.COLUMN_UPDATED_AT, DateTimeUtils.nowMillis())
        }
        return resolver.update(AiMemories.buildItemUri(id), values, null, null) > 0
    }

    fun getMemoryById(id: Long): AiMemory? {
        require(id > 0L) { "Memory id is required." }
        resolver.query(AiMemories.buildItemUri(id), null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.toMemory()
        }
        return null
    }

    fun getMemoriesForManagement(): List<AiMemory> = queryMemories(null, null)

    fun getActiveMemories(nowMillis: Long = DateTimeUtils.nowMillis()): List<AiMemory> =
        queryMemories(
            "${AiMemories.COLUMN_IS_ENABLED}=1 AND (${AiMemories.COLUMN_IS_PINNED}=1 OR " +
                "${AiMemories.COLUMN_EXPIRES_AT} IS NULL OR ${AiMemories.COLUMN_EXPIRES_AT}>?)",
            arrayOf(nowMillis.toString())
        )

    fun getMemoryCount(): Int = countMatching(null, null)

    fun setEnabled(id: Long, enabled: Boolean): Boolean =
        updateFlag(id, AiMemories.COLUMN_IS_ENABLED, enabled)

    fun setPinned(id: Long, pinned: Boolean): Boolean =
        updateFlag(id, AiMemories.COLUMN_IS_PINNED, pinned)

    fun deleteMemory(id: Long): Boolean {
        require(id > 0L) { "Memory id is required." }
        return resolver.delete(AiMemories.buildItemUri(id), null, null) > 0
    }

    fun countStale(nowMillis: Long = DateTimeUtils.nowMillis()): Int =
        countMatching(STALE_SELECTION, arrayOf(nowMillis.toString()))

    /** Explicit user cleanup: pinned rows are retained even when their expiry has passed. */
    fun pruneExpired(nowMillis: Long = DateTimeUtils.nowMillis()): Int =
        resolver.delete(AiMemories.CONTENT_URI, STALE_SELECTION, arrayOf(nowMillis.toString()))

    private fun updateFlag(id: Long, column: String, enabled: Boolean): Boolean {
        require(id > 0L) { "Memory id is required." }
        val values = ContentValues().apply {
            put(column, if (enabled) 1 else 0)
            put(AiMemories.COLUMN_UPDATED_AT, DateTimeUtils.nowMillis())
        }
        return resolver.update(AiMemories.buildItemUri(id), values, null, null) > 0
    }

    private fun queryMemories(selection: String?, selectionArgs: Array<String>?): List<AiMemory> {
        val memories = mutableListOf<AiMemory>()
        resolver.query(
            AiMemories.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            "${AiMemories.COLUMN_IS_PINNED} DESC, ${AiMemories.COLUMN_UPDATED_AT} DESC, ${BaseColumns._ID} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) memories += cursor.toMemory()
        }
        return memories
    }

    private fun countMatching(selection: String?, selectionArgs: Array<String>?): Int =
        resolver.query(
            AiMemories.CONTENT_URI,
            arrayOf(BaseColumns._ID),
            selection,
            selectionArgs,
            null
        )?.use { it.count } ?: 0

    private fun AiMemoryDraft.toContentValues(): ContentValues = ContentValues().apply {
        put(AiMemories.COLUMN_CATEGORY, category)
        put(AiMemories.COLUMN_CONTENT, content)
        put(AiMemories.COLUMN_IS_ENABLED, if (isEnabled) 1 else 0)
        put(AiMemories.COLUMN_IS_PINNED, if (isPinned) 1 else 0)
        put(AiMemories.COLUMN_EXPIRES_AT, expiresAt)
    }

    private fun Cursor.toMemory(): AiMemory = AiMemory(
        id = getRequiredLong(BaseColumns._ID),
        category = getRequiredInt(AiMemories.COLUMN_CATEGORY),
        content = getRequiredString(AiMemories.COLUMN_CONTENT),
        isEnabled = getBooleanFlag(AiMemories.COLUMN_IS_ENABLED),
        isPinned = getBooleanFlag(AiMemories.COLUMN_IS_PINNED),
        expiresAt = getNullableLong(AiMemories.COLUMN_EXPIRES_AT),
        createdAt = getRequiredLong(AiMemories.COLUMN_CREATED_AT),
        updatedAt = getRequiredLong(AiMemories.COLUMN_UPDATED_AT)
    )

    private companion object {
        const val STALE_SELECTION = "${AiMemories.COLUMN_IS_PINNED}=0 AND " +
            "${AiMemories.COLUMN_EXPIRES_AT} IS NOT NULL AND ${AiMemories.COLUMN_EXPIRES_AT}<=?"
    }
}
