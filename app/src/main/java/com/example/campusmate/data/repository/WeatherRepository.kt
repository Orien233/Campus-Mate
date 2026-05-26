package com.example.campusmate.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import com.example.campusmate.data.db.CampusMateContract
import com.example.campusmate.domain.weather.MockWeatherDataSource
import com.example.campusmate.domain.weather.RemoteWeatherDataSource
import com.example.campusmate.domain.weather.WeatherDataSource
import com.example.campusmate.domain.weather.WeatherResult
import com.example.campusmate.util.DateTimeUtils
import com.example.campusmate.util.DbUtils.getNullableString
import com.example.campusmate.util.DbUtils.getRequiredLong
import com.example.campusmate.util.DbUtils.getRequiredString

/** Weather cache and source orchestration through ContentResolver. */
class WeatherRepository(
    context: Context,
    private val remoteDataSource: WeatherDataSource = RemoteWeatherDataSource(),
    private val mockDataSource: WeatherDataSource = MockWeatherDataSource()
) {
    private val resolver = context.applicationContext.contentResolver

    fun getWeather(city: String, useMock: Boolean, forceRefresh: Boolean = false): WeatherResult {
        val normalizedCity = city.trim().ifBlank { DEFAULT_CITY }
        val freshCache = getCachedWeather(normalizedCity)
            ?.takeIf { !forceRefresh && isCacheFresh(it, CACHE_MAX_AGE_MILLIS) }
        if (freshCache != null) return freshCache

        val fetched = if (useMock) {
            mockDataSource.fetchWeather(normalizedCity) ?: fallbackMock(normalizedCity)
        } else {
            remoteDataSource.fetchWeather(normalizedCity)
                ?: getCachedWeather(normalizedCity)
                ?: mockDataSource.fetchWeather(normalizedCity)
                ?: fallbackMock(normalizedCity)
        }

        saveWeatherCache(fetched)
        return fetched
    }

    fun getCachedWeather(city: String? = null): WeatherResult? {
        val selection = city?.takeIf { it.isNotBlank() }?.let {
            "${CampusMateContract.WeatherCache.COLUMN_CITY}=?"
        }
        val selectionArgs = city?.takeIf { it.isNotBlank() }?.let { arrayOf(it.trim()) }
        return queryWeather(
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = "${CampusMateContract.WeatherCache.COLUMN_UPDATED_AT} DESC LIMIT 1"
        ).firstOrNull()
    }

    fun saveWeatherCache(weather: WeatherResult): Long {
        val normalizedCity = weather.city.trim().ifBlank { DEFAULT_CITY }
        resolver.delete(
            CampusMateContract.WeatherCache.CONTENT_URI,
            "${CampusMateContract.WeatherCache.COLUMN_CITY}=?",
            arrayOf(normalizedCity)
        )
        val uri = resolver.insert(
            CampusMateContract.WeatherCache.CONTENT_URI,
            weather.copy(city = normalizedCity, updatedAt = weather.updatedAt.takeIf { it > 0L } ?: DateTimeUtils.nowMillis())
                .toContentValues()
        )
        return uri?.let(ContentUris::parseId) ?: -1L
    }

    fun isCacheFresh(maxAgeMillis: Long): Boolean {
        return getCachedWeather()?.let { isCacheFresh(it, maxAgeMillis) } ?: false
    }

    private fun isCacheFresh(weather: WeatherResult, maxAgeMillis: Long): Boolean {
        return DateTimeUtils.nowMillis() - weather.updatedAt <= maxAgeMillis
    }

    private fun queryWeather(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<WeatherResult> {
        val results = mutableListOf<WeatherResult>()
        resolver.query(
            CampusMateContract.WeatherCache.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    WeatherResult(
                        city = cursor.getRequiredString(CampusMateContract.WeatherCache.COLUMN_CITY),
                        weatherText = cursor.getRequiredString(CampusMateContract.WeatherCache.COLUMN_WEATHER_TEXT),
                        temperature = cursor.getRequiredString(CampusMateContract.WeatherCache.COLUMN_TEMPERATURE),
                        humidity = cursor.getRequiredString(CampusMateContract.WeatherCache.COLUMN_HUMIDITY),
                        wind = cursor.getRequiredString(CampusMateContract.WeatherCache.COLUMN_WIND),
                        source = cursor.getRequiredString(CampusMateContract.WeatherCache.COLUMN_SOURCE),
                        rawJson = cursor.getNullableString(CampusMateContract.WeatherCache.COLUMN_RAW_JSON),
                        updatedAt = cursor.getRequiredLong(CampusMateContract.WeatherCache.COLUMN_UPDATED_AT)
                    )
                )
            }
        }
        return results
    }

    private fun fallbackMock(city: String): WeatherResult {
        return WeatherResult(
            city = city,
            weatherText = "\u6674",
            temperature = "26\u00B0C",
            humidity = "45%",
            wind = "\u4e1c\u5357\u98ce 3 \u7ea7",
            source = "mock",
            rawJson = null,
            updatedAt = DateTimeUtils.nowMillis()
        )
    }

    private fun WeatherResult.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(CampusMateContract.WeatherCache.COLUMN_CITY, city)
            put(CampusMateContract.WeatherCache.COLUMN_WEATHER_TEXT, weatherText)
            put(CampusMateContract.WeatherCache.COLUMN_TEMPERATURE, temperature)
            put(CampusMateContract.WeatherCache.COLUMN_HUMIDITY, humidity)
            put(CampusMateContract.WeatherCache.COLUMN_WIND, wind)
            put(CampusMateContract.WeatherCache.COLUMN_SOURCE, source)
            put(CampusMateContract.WeatherCache.COLUMN_RAW_JSON, rawJson)
            put(CampusMateContract.WeatherCache.COLUMN_UPDATED_AT, updatedAt)
        }
    }

    companion object {
        const val CACHE_MAX_AGE_MILLIS = 30 * 60 * 1000L
        const val DEFAULT_CITY = "\u5317\u4eac"
    }
}
