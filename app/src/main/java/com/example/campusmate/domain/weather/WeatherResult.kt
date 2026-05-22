package com.example.campusmate.domain.weather

/** Weather data displayed on Dashboard and cached locally. */
data class WeatherResult(
    val city: String,
    val weatherText: String,
    val temperature: String,
    val humidity: String,
    val wind: String,
    val source: String,
    val rawJson: String? = null,
    val updatedAt: Long = 0L
)
