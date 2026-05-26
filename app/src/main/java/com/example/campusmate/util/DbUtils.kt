package com.example.campusmate.util

import android.database.Cursor

/** Cursor helpers used by repositories to keep mappings explicit and safe. */
object DbUtils {
    fun Cursor.getRequiredLong(columnName: String): Long = getLong(getColumnIndexOrThrow(columnName))

    fun Cursor.getRequiredInt(columnName: String): Int = getInt(getColumnIndexOrThrow(columnName))

    fun Cursor.getRequiredString(columnName: String): String = getString(getColumnIndexOrThrow(columnName))

    fun Cursor.getNullableLong(columnName: String): Long? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getLong(index)
    }

    fun Cursor.getNullableString(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    fun Cursor.getBooleanFlag(columnName: String): Boolean {
        return getRequiredInt(columnName) != 0
    }
}
