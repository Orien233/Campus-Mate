package com.example.campusmate.data.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.db.CampusMateDbHelper

/** ContentProvider boundary for all CampusMate local data access. */
class CampusMateProvider : ContentProvider() {
    private lateinit var dbHelper: CampusMateDbHelper

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        dbHelper = CampusMateDbHelper(appContext)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val match = uriMatcher.match(uri)
        val queryBuilder = SQLiteQueryBuilder().apply {
            tables = tableForMatch(match)
            itemIdForMatch(match, uri)?.let { id ->
                appendWhere("${BaseColumns._ID}=$id")
            }
        }

        val cursor = queryBuilder.query(
            dbHelper.readableDatabase,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            COURSES -> CampusMateContract.Courses.CONTENT_TYPE
            COURSE_ID -> CampusMateContract.Courses.CONTENT_ITEM_TYPE
            TASKS -> CampusMateContract.StudyTasks.CONTENT_TYPE
            TASK_ID -> CampusMateContract.StudyTasks.CONTENT_ITEM_TYPE
            FOCUS_SESSIONS -> CampusMateContract.FocusSessions.CONTENT_TYPE
            FOCUS_SESSION_ID -> CampusMateContract.FocusSessions.CONTENT_ITEM_TYPE
            STUDY_RECORDS -> CampusMateContract.StudyRecords.CONTENT_TYPE
            STUDY_RECORD_ID -> CampusMateContract.StudyRecords.CONTENT_ITEM_TYPE
            IMPORT_LOGS -> CampusMateContract.ImportLogs.CONTENT_TYPE
            IMPORT_LOG_ID -> CampusMateContract.ImportLogs.CONTENT_ITEM_TYPE
            USER_PROFILE -> CampusMateContract.UserProfile.CONTENT_TYPE
            USER_PROFILE_ID -> CampusMateContract.UserProfile.CONTENT_ITEM_TYPE
            STUDY_BUDDIES -> CampusMateContract.StudyBuddies.CONTENT_TYPE
            STUDY_BUDDY_ID -> CampusMateContract.StudyBuddies.CONTENT_ITEM_TYPE
            WEATHER_CACHE -> CampusMateContract.WeatherCache.CONTENT_TYPE
            WEATHER_CACHE_ID -> CampusMateContract.WeatherCache.CONTENT_ITEM_TYPE
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val match = uriMatcher.match(uri)
        if (!isCollectionMatch(match)) {
            throw IllegalArgumentException("Insert does not support item URI: $uri")
        }

        val table = tableForMatch(match)
        val rowId = dbHelper.writableDatabase.insert(table, null, values ?: ContentValues())
        if (rowId <= 0L) {
            throw SQLException("Failed to insert row into $uri")
        }

        val insertedUri = ContentUris.withAppendedId(collectionUriForMatch(match), rowId)
        notifyChange(insertedUri)
        return insertedUri
    }

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int {
        val match = uriMatcher.match(uri)
        if (!isCollectionMatch(match)) {
            throw IllegalArgumentException("Bulk insert does not support item URI: $uri")
        }

        val db = dbHelper.writableDatabase
        val table = tableForMatch(match)
        var insertedCount = 0
        db.beginTransaction()
        try {
            values.forEach { contentValues ->
                val rowId = db.insert(table, null, contentValues)
                if (rowId > 0L) {
                    insertedCount++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (insertedCount > 0) {
            notifyChange(uri)
        }
        return insertedCount
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val match = uriMatcher.match(uri)
        val table = tableForMatch(match)
        val (finalSelection, finalSelectionArgs) = withItemSelection(match, uri, selection, selectionArgs)
        val rows = dbHelper.writableDatabase.update(table, values, finalSelection, finalSelectionArgs)
        if (rows > 0) {
            notifyChange(uri)
        }
        return rows
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val match = uriMatcher.match(uri)
        val table = tableForMatch(match)
        val (finalSelection, finalSelectionArgs) = withItemSelection(match, uri, selection, selectionArgs)
        val rows = dbHelper.writableDatabase.delete(table, finalSelection, finalSelectionArgs)
        if (rows > 0) {
            notifyChange(uri)
        }
        return rows
    }

    private fun tableForMatch(match: Int): String {
        return when (match) {
            COURSES, COURSE_ID -> CampusMateContract.Courses.TABLE_NAME
            TASKS, TASK_ID -> CampusMateContract.StudyTasks.TABLE_NAME
            FOCUS_SESSIONS, FOCUS_SESSION_ID -> CampusMateContract.FocusSessions.TABLE_NAME
            STUDY_RECORDS, STUDY_RECORD_ID -> CampusMateContract.StudyRecords.TABLE_NAME
            IMPORT_LOGS, IMPORT_LOG_ID -> CampusMateContract.ImportLogs.TABLE_NAME
            USER_PROFILE, USER_PROFILE_ID -> CampusMateContract.UserProfile.TABLE_NAME
            STUDY_BUDDIES, STUDY_BUDDY_ID -> CampusMateContract.StudyBuddies.TABLE_NAME
            WEATHER_CACHE, WEATHER_CACHE_ID -> CampusMateContract.WeatherCache.TABLE_NAME
            else -> throw IllegalArgumentException("Unknown URI match: $match")
        }
    }

    private fun itemIdForMatch(match: Int, uri: Uri): Long? {
        return when (match) {
            COURSE_ID,
            TASK_ID,
            FOCUS_SESSION_ID,
            STUDY_RECORD_ID,
            IMPORT_LOG_ID,
            USER_PROFILE_ID,
            STUDY_BUDDY_ID,
            WEATHER_CACHE_ID -> ContentUris.parseId(uri)
            else -> null
        }
    }

    private fun withItemSelection(
        match: Int,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Pair<String?, Array<String>?> {
        val id = itemIdForMatch(match, uri) ?: return Pair(selection, selectionArgs?.map { it }?.toTypedArray())
        val idSelection = "${BaseColumns._ID}=?"
        val idSelectionArgs = arrayOf(id.toString())
        if (selection.isNullOrBlank()) {
            return Pair(idSelection, idSelectionArgs)
        }
        return Pair("($idSelection) AND ($selection)", idSelectionArgs + selectionArgs.orEmpty())
    }

    private fun collectionUriForMatch(match: Int): Uri {
        return when (match) {
            COURSES -> CampusMateContract.Courses.CONTENT_URI
            TASKS -> CampusMateContract.StudyTasks.CONTENT_URI
            FOCUS_SESSIONS -> CampusMateContract.FocusSessions.CONTENT_URI
            STUDY_RECORDS -> CampusMateContract.StudyRecords.CONTENT_URI
            IMPORT_LOGS -> CampusMateContract.ImportLogs.CONTENT_URI
            USER_PROFILE -> CampusMateContract.UserProfile.CONTENT_URI
            STUDY_BUDDIES -> CampusMateContract.StudyBuddies.CONTENT_URI
            WEATHER_CACHE -> CampusMateContract.WeatherCache.CONTENT_URI
            else -> throw IllegalArgumentException("Not a collection match: $match")
        }
    }

    private fun isCollectionMatch(match: Int): Boolean {
        return match == COURSES ||
            match == TASKS ||
            match == FOCUS_SESSIONS ||
            match == STUDY_RECORDS ||
            match == IMPORT_LOGS ||
            match == USER_PROFILE ||
            match == STUDY_BUDDIES ||
            match == WEATHER_CACHE
    }

    private fun notifyChange(uri: Uri) {
        context?.contentResolver?.notifyChange(uri, null)
    }

    companion object {
        private const val COURSES = 100
        private const val COURSE_ID = 101
        private const val TASKS = 200
        private const val TASK_ID = 201
        private const val FOCUS_SESSIONS = 300
        private const val FOCUS_SESSION_ID = 301
        private const val STUDY_RECORDS = 400
        private const val STUDY_RECORD_ID = 401
        private const val IMPORT_LOGS = 500
        private const val IMPORT_LOG_ID = 501
        private const val USER_PROFILE = 600
        private const val USER_PROFILE_ID = 601
        private const val STUDY_BUDDIES = 700
        private const val STUDY_BUDDY_ID = 701
        private const val WEATHER_CACHE = 800
        private const val WEATHER_CACHE_ID = 801

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_COURSES, COURSES)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_COURSES}/#", COURSE_ID)
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_TASKS, TASKS)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_TASKS}/#", TASK_ID)
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_FOCUS_SESSIONS, FOCUS_SESSIONS)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_FOCUS_SESSIONS}/#", FOCUS_SESSION_ID)
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_STUDY_RECORDS, STUDY_RECORDS)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_STUDY_RECORDS}/#", STUDY_RECORD_ID)
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_IMPORT_LOGS, IMPORT_LOGS)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_IMPORT_LOGS}/#", IMPORT_LOG_ID)
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_USER_PROFILE, USER_PROFILE)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_USER_PROFILE}/#", USER_PROFILE_ID)
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_STUDY_BUDDIES, STUDY_BUDDIES)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_STUDY_BUDDIES}/#", STUDY_BUDDY_ID)
            addURI(CampusMateContract.AUTHORITY, CampusMateContract.PATH_WEATHER_CACHE, WEATHER_CACHE)
            addURI(CampusMateContract.AUTHORITY, "${CampusMateContract.PATH_WEATHER_CACHE}/#", WEATHER_CACHE_ID)
        }
    }
}
