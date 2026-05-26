package com.example.campusmate

import com.example.campusmate.domain.weather.RemoteWeatherDataSource
import com.example.campusmate.domain.weather.WeatherParser
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherParserInstrumentedTest {
    @Test
    fun parseWttr_readsCurrentCondition() {
        val rawJson = """
            {
              "current_condition": [
                {
                  "temp_C": "23",
                  "humidity": "56",
                  "windspeedKmph": "9",
                  "winddir16Point": "NE",
                  "weatherDesc": [{"value": "Cloudy"}]
                }
              ]
            }
        """.trimIndent()

        val result = WeatherParser.parseWttr("Shanghai", rawJson)

        assertEquals("Shanghai", result.city)
        assertEquals("Cloudy", result.weatherText)
        assertEquals("23\u00B0C", result.temperature)
        assertEquals("56%", result.humidity)
        assertEquals("NE 9 km/h", result.wind)
        assertEquals(RemoteWeatherDataSource.SOURCE, result.source)
    }
}
