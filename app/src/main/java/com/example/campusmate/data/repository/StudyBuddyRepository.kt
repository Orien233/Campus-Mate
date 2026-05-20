package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getNullableString
import com.example.campusmate.util.DbUtils.getRequiredInt
import com.example.campusmate.util.DbUtils.getRequiredLong
import com.example.campusmate.util.DbUtils.getRequiredString

/** Repository for QR/NFC study partners, always accessed through ContentResolver. */
class StudyBuddyRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun addBuddy(buddy: StudyBuddy): Long {
        validateBuddy(buddy)
        val uri = resolver.insert(
            CampusMateContract.StudyBuddies.CONTENT_URI,
            buddy.toContentValues(addedAt = DateTimeUtils.nowMillis())
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }

    fun getAllBuddies(): List<StudyBuddy> {
        return queryBuddies(
            sortOrder = "${CampusMateContract.StudyBuddies.COLUMN_ADDED_AT} DESC"
        )
    }

    fun getBuddyById(id: Long): StudyBuddy? {
        require(id > 0L) { "Buddy id is required." }
        return queryBuddies(uri = CampusMateContract.StudyBuddies.buildItemUri(id)).firstOrNull()
    }

    fun deleteBuddy(id: Long): Boolean {
        require(id > 0L) { "Buddy id is required for delete." }
        return resolver.delete(CampusMateContract.StudyBuddies.buildItemUri(id), null, null) > 0
    }

    fun existsByGithubOrEmail(github: String?, email: String?): Boolean {
        val githubValue = github?.trimToNull()
        val emailValue = email?.trimToNull()
        if (githubValue == null && emailValue == null) return false

        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        githubValue?.let {
            clauses.add("${CampusMateContract.StudyBuddies.COLUMN_GITHUB}=?")
            args.add(it)
        }
        emailValue?.let {
            clauses.add("${CampusMateContract.StudyBuddies.COLUMN_EMAIL}=?")
            args.add(it)
        }

        resolver.query(
            CampusMateContract.StudyBuddies.CONTENT_URI,
            arrayOf(BaseColumns._ID),
            clauses.joinToString(" OR "),
            args.toTypedArray(),
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun queryBuddies(
        uri: android.net.Uri = CampusMateContract.StudyBuddies.CONTENT_URI,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<StudyBuddy> {
        val buddies = mutableListOf<StudyBuddy>()
        resolver.query(uri, null, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                buddies.add(
                    StudyBuddy(
                        id = cursor.getRequiredLong(BaseColumns._ID),
                        nickname = cursor.getRequiredString(CampusMateContract.StudyBuddies.COLUMN_NICKNAME),
                        school = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_SCHOOL),
                        major = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_MAJOR),
                        grade = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_GRADE),
                        bio = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_BIO),
                        github = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_GITHUB),
                        email = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_EMAIL),
                        phone = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_PHONE),
                        source = cursor.getRequiredInt(CampusMateContract.StudyBuddies.COLUMN_SOURCE),
                        addedAt = cursor.getRequiredLong(CampusMateContract.StudyBuddies.COLUMN_ADDED_AT),
                        note = cursor.getNullableString(CampusMateContract.StudyBuddies.COLUMN_NOTE)
                    )
                )
            }
        }
        return buddies
    }

    private fun StudyBuddy.toContentValues(addedAt: Long? = null): ContentValues {
        return ContentValues().apply {
            put(CampusMateContract.StudyBuddies.COLUMN_NICKNAME, nickname.trim())
            put(CampusMateContract.StudyBuddies.COLUMN_SCHOOL, school?.trimToNull())
            put(CampusMateContract.StudyBuddies.COLUMN_MAJOR, major?.trimToNull())
            put(CampusMateContract.StudyBuddies.COLUMN_GRADE, grade?.trimToNull())
            put(CampusMateContract.StudyBuddies.COLUMN_BIO, bio?.trimToNull())
            put(CampusMateContract.StudyBuddies.COLUMN_GITHUB, github?.trimToNull())
            put(CampusMateContract.StudyBuddies.COLUMN_EMAIL, email?.trimToNull())
            put(CampusMateContract.StudyBuddies.COLUMN_PHONE, phone?.trimToNull())
            put(CampusMateContract.StudyBuddies.COLUMN_SOURCE, source)
            addedAt?.let { put(CampusMateContract.StudyBuddies.COLUMN_ADDED_AT, it) }
            put(CampusMateContract.StudyBuddies.COLUMN_NOTE, note?.trimToNull())
        }
    }

    private fun validateBuddy(buddy: StudyBuddy) {
        require(buddy.nickname.isNotBlank()) { "Buddy nickname cannot be blank." }
        require(buddy.source in StudyBuddy.SOURCE_QR..StudyBuddy.SOURCE_MANUAL) { "Unsupported buddy source." }
    }

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }
}
