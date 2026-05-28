package com.example.campusmate.ui.task

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

object TaskAttachmentUiUtils {
    fun queryDisplayName(context: Context, uri: Uri): String? {
        val resolver = context.applicationContext.contentResolver
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }
}
