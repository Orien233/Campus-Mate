package com.example.campusmate.domain.import_

import java.io.Serializable

/** Parsed course candidate before the user confirms schedule import. */
data class CourseDraft(
    val name: String,
    val teacher: String? = null,
    val classroom: String? = null,
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int = 1,
    val endWeek: Int = 18,
    val weekType: Int = 0,
    val color: String? = null,
    val note: String? = null,
    val sourceText: String? = null
) : Serializable
