package com.example.campusmate.ui.profile

import android.content.Context
import com.example.campusmate.R
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.util.DateTimeUtils

object ProfileUiFormatter {
    fun optional(value: String?, fallback: String = "未填写"): String = value.orEmpty().ifBlank { fallback }

    fun sourceLabel(context: Context, source: Int): String {
        return when (source) {
            StudyBuddy.SOURCE_NFC -> context.getString(R.string.buddy_source_nfc)
            StudyBuddy.SOURCE_MANUAL -> context.getString(R.string.buddy_source_manual)
            else -> context.getString(R.string.buddy_source_qr)
        }
    }

    fun addedAtLabel(context: Context, addedAt: Long): String {
        return if (addedAt > 0L) {
            context.getString(R.string.buddy_added_at, DateTimeUtils.formatDateTime(addedAt))
        } else {
            context.getString(R.string.buddy_added_at, optional(null))
        }
    }

    fun schoolLine(school: String?, major: String?, grade: String?): String {
        return listOfNotNull(
            school?.trim()?.takeIf { it.isNotEmpty() },
            major?.trim()?.takeIf { it.isNotEmpty() },
            grade?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" · ").ifBlank { "未填写学校信息" }
    }
}
