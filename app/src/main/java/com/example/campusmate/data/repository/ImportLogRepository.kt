package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.ImportLog
import com.example.campusmate.util.DateTimeUtils

/** Repository for persisting schedule import summaries through ContentResolver. */
class ImportLogRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun addImportLog(log: ImportLog): Long {
        val uri = resolver.insert(
            CampusMateContract.ImportLogs.CONTENT_URI,
            ContentValues().apply {
                put(CampusMateContract.ImportLogs.COLUMN_SOURCE_TYPE, log.sourceType)
                put(CampusMateContract.ImportLogs.COLUMN_IMPORTED_COUNT, log.importedCount)
                put(CampusMateContract.ImportLogs.COLUMN_SKIPPED_COUNT, log.skippedCount)
                put(CampusMateContract.ImportLogs.COLUMN_CONFLICT_COUNT, log.conflictCount)
                put(CampusMateContract.ImportLogs.COLUMN_CREATED_AT, log.createdAt.takeIf { it > 0L } ?: DateTimeUtils.nowMillis())
                put(CampusMateContract.ImportLogs.COLUMN_MESSAGE, log.message)
            }
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }
}
