package com.example.campusmate.domain.weather

import com.example.campusmate.util.DateTimeUtils

/** Stable weather data for classroom demos and offline use. */
class MockWeatherDataSource : WeatherDataSource {
    override fun fetchWeather(city: String): WeatherResult {
        val normalizedCity = city.trim().ifBlank { DEFAULT_CITY }
        val sample = samples[normalizedCity.lowercase()] ?: defaultSample
        return WeatherResult(
            city = normalizedCity,
            weatherText = sample.weatherText,
            temperature = sample.temperature,
            humidity = sample.humidity,
            wind = sample.wind,
            source = SOURCE,
            rawJson = """{"source":"$SOURCE","city":"$normalizedCity"}""",
            updatedAt = DateTimeUtils.nowMillis()
        )
    }

    private data class MockSample(
        val weatherText: String,
        val temperature: String,
        val humidity: String,
        val wind: String
    )

    companion object {
        const val SOURCE = "mock"
        private const val DEFAULT_CITY = "\u5317\u4eac"
        private val defaultSample = MockSample("\u6674", "26\u00B0C", "45%", "\u4e1c\u5357\u98ce 3 \u7ea7")
        private val samples = mapOf(
            "\u5317\u4eac" to MockSample("\u6674", "26\u00B0C", "45%", "\u4e1c\u5357\u98ce 3 \u7ea7"),
            "\u4e0a\u6d77" to MockSample("\u591a\u4e91", "28\u00B0C", "62%", "\u4e1c\u98ce 2 \u7ea7"),
            "\u5929\u6d25" to MockSample("\u9634", "24\u00B0C", "55%", "\u5317\u98ce 2 \u7ea7"),
            "tokyo" to MockSample("Cloudy", "23\u00B0C", "58%", "NE 3")
        )
    }
}
