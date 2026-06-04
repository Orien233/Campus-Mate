package com.example.campusmate.domain.weather

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

/** Optional remote weather source with no hardcoded API key. */
class RemoteWeatherDataSource : WeatherDataSource {
    override fun fetchWeather(city: String): WeatherResult? {
        val normalizedCity = city.trim()
        if (normalizedCity.isBlank()) return null

        return runCatching {
            val encodedCity = URLEncoder.encode(normalizedCity, StandardCharsets.UTF_8.name())
            val url = URL("https://wttr.in/$encodedCity?format=j1&lang=zh")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CampusMate-Android")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val rawJson = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                WeatherParser.parseWttr(normalizedCity, rawJson)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        const val SOURCE = "wttr.in"
        private const val TIMEOUT_MILLIS = 4_000
    }
}
