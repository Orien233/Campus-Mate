package com.example.campusmate.data.model

/** Aggregated duration and count for one calendar date. */
data class DailyStudyStat(
    val recordDate: String,
    val durationSec: Int,
    val recordCount: Int
)
