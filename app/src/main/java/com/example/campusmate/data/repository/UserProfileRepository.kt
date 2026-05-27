package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.campusmate.app.AppConfig
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.data.model.StudyBuddy
import com.example.campusmate.data.model.UserProfile
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getBooleanFlag
import com.example.campusmate.util.DbUtils.getNullableString
import com.example.campusmate.util.DbUtils.getRequiredLong
import com.example.campusmate.util.DbUtils.getRequiredString
import org.json.JSONObject

/** Repository for the local study-card profile and its public JSON payload. */
class UserProfileRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun getProfile(): UserProfile? {
        return queryProfiles(
            sortOrder = "${CampusMateContract.UserProfile.COLUMN_UPDATED_AT} DESC LIMIT 1"
        ).firstOrNull()
    }

    fun saveProfile(profile: UserProfile): Long {
        validateProfile(profile)
        val now = DateTimeUtils.nowMillis()
        val existing = if (profile.id > 0L) profile else getProfile()
        return if (existing != null && existing.id > 0L) {
            resolver.update(
                CampusMateContract.UserProfile.buildItemUri(existing.id),
                profile.toContentValues(
                    createdAt = existing.createdAt.takeIf { it > 0L },
                    updatedAt = now
                ),
                null,
                null
            )
            existing.id
        } else {
            val uri = resolver.insert(
                CampusMateContract.UserProfile.CONTENT_URI,
                profile.toContentValues(createdAt = now, updatedAt = now)
            )
            uri?.let(ContentUris::parseId) ?: -1L
        }
    }

    fun buildPublicProfileJson(): String {
        val profile = getProfile() ?: throw IllegalStateException("Profile is empty.")
        validateProfile(profile)
        val json = JSONObject()
            .put("app", AppConfig.APP_NAME)
            .put("version", PUBLIC_PROFILE_VERSION)
            .put("nickname", profile.nickname.trim())
        json.putIfNotBlank("school", profile.school)
        json.putIfNotBlank("major", profile.major)
        json.putIfNotBlank("grade", profile.grade)
        json.putIfNotBlank("bio", profile.bio)
        json.putIfNotBlank("github", profile.github)
        if (profile.showEmail) {
            json.putIfNotBlank("email", profile.email)
        }
        if (profile.showPhone) {
            json.putIfNotBlank("phone", profile.phone)
        }
        return json.toString()
    }

    fun parsePublicProfileJson(json: String, source: Int = StudyBuddy.SOURCE_QR): StudyBuddy {
        val payload = JSONObject(json)
        val app = payload.optString("app")
        if (app != AppConfig.APP_NAME) {
            throw IllegalArgumentException("Unsupported profile app: $app")
        }
        val version = payload.optInt("version", -1)
        if (version != PUBLIC_PROFILE_VERSION) {
            throw IllegalArgumentException("Unsupported profile version: $version")
        }
        val nickname = payload.optString("nickname").trim()
        require(nickname.isNotBlank()) { "Profile nickname is required." }
        return StudyBuddy(
            nickname = nickname,
            school = payload.optNullableString("school"),
            major = payload.optNullableString("major"),
            grade = payload.optNullableString("grade"),
            bio = payload.optNullableString("bio"),
            github = payload.optNullableString("github"),
            email = payload.optNullableString("email"),
            phone = payload.optNullableString("phone"),
            source = source
        )
    }

    private fun queryProfiles(sortOrder: String? = null): List<UserProfile> {
        val profiles = mutableListOf<UserProfile>()
        resolver.query(CampusMateContract.UserProfile.CONTENT_URI, null, null, null, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                profiles.add(
                    UserProfile(
                        id = cursor.getRequiredLong(BaseColumns._ID),
                        nickname = cursor.getRequiredString(CampusMateContract.UserProfile.COLUMN_NICKNAME),
                        school = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_SCHOOL),
                        major = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_MAJOR),
                        grade = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_GRADE),
                        bio = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_BIO),
                        avatarUri = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_AVATAR_URI),
                        github = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_GITHUB),
                        email = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_EMAIL),
                        phone = cursor.getNullableString(CampusMateContract.UserProfile.COLUMN_PHONE),
                        showEmail = cursor.getBooleanFlag(CampusMateContract.UserProfile.COLUMN_SHOW_EMAIL),
                        showPhone = cursor.getBooleanFlag(CampusMateContract.UserProfile.COLUMN_SHOW_PHONE),
                        createdAt = cursor.getRequiredLong(CampusMateContract.UserProfile.COLUMN_CREATED_AT),
                        updatedAt = cursor.getRequiredLong(CampusMateContract.UserProfile.COLUMN_UPDATED_AT)
                    )
                )
            }
        }
        return profiles
    }

    private fun UserProfile.toContentValues(createdAt: Long? = null, updatedAt: Long? = null): ContentValues {
        return ContentValues().apply {
            put(CampusMateContract.UserProfile.COLUMN_NICKNAME, nickname.trim())
            put(CampusMateContract.UserProfile.COLUMN_SCHOOL, school?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_MAJOR, major?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_GRADE, grade?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_BIO, bio?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_AVATAR_URI, avatarUri?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_GITHUB, github?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_EMAIL, email?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_PHONE, phone?.trimToNull())
            put(CampusMateContract.UserProfile.COLUMN_SHOW_EMAIL, if (showEmail) 1 else 0)
            put(CampusMateContract.UserProfile.COLUMN_SHOW_PHONE, if (showPhone) 1 else 0)
            createdAt?.let { put(CampusMateContract.UserProfile.COLUMN_CREATED_AT, it) }
            updatedAt?.let { put(CampusMateContract.UserProfile.COLUMN_UPDATED_AT, it) }
        }
    }

    private fun validateProfile(profile: UserProfile) {
        require(profile.nickname.isNotBlank()) { "Profile nickname cannot be blank." }
    }

    private fun JSONObject.putIfNotBlank(name: String, value: String?) {
        val trimmed = value?.trimToNull() ?: return
        put(name, trimmed)
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trimToNull()
    }

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

    companion object {
        const val PUBLIC_PROFILE_VERSION = 1
    }
}
