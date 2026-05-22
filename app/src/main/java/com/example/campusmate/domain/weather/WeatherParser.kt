package com.example.campusmate.domain.weather

import com.example.campusmate.util.DateTimeUtils
import org.json.JSONObject

/** Parses simple JSON weather responses from wttr.in. */
object WeatherParser {
    fun parseWttr(city: String, rawJson: String): WeatherResult {
        val root = JSONObject(rawJson)
        val current = root.getJSONArray("current_condition").getJSONObject(0)
        val weatherText = current
            .optJSONArray("lang_zh")
            ?.optJSONObject(0)
            ?.optString("value")
            ?.takeIf { it.isNotBlank() }
            ?: current
                .optJSONArray("weatherDesc")
                ?.optJSONObject(0)
                ?.optString("value")
                ?.takeIf { it.isNotBlank() }
            ?: "\u672a\u77e5"

        return WeatherResult(
            city = city.trim(),
            weatherText = weatherText,
            temperature = "${current.optString("temp_C", "--")}\u00B0C",
            humidity = "${current.optString("humidity", "--")}%",
            wind = "${current.optString("winddir16Point", "--")} ${current.optString("windspeedKmph", "--")} km/h",
            source = RemoteWeatherDataSource.SOURCE,
            rawJson = rawJson,
            updatedAt = DateTimeUtils.nowMillis()
        )
    }
}
