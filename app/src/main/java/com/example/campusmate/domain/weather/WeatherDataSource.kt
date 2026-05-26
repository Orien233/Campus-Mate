package com.example.campusmate.domain.weather

/** Fetches weather for a manually configured city. */
interface WeatherDataSource {
    fun fetchWeather(city: String): WeatherResult?
}
