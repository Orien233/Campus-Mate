package com.example.campusmate.data.model

/** Local-only profile used to build a public CampusMate study card. */
data class UserProfile(
    val id: Long = 0L,
    val nickname: String = "",
    val school: String? = null,
    val major: String? = null,
    val grade: String? = null,
    val bio: String? = null,
    val avatarUri: String? = null,
    val github: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val showEmail: Boolean = false,
    val showPhone: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
