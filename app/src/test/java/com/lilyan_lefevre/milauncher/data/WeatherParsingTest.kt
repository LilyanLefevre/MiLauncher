package com.lilyan_lefevre.milauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherParsingTest {

    /** 48 h of temperatures (0°, 1°, 2°…) so values match their indices. */
    private fun forecastJson(currentTime: String): String {
        val times = (0 until 48).joinToString(",") { "\"2026-10-${20 + it / 24}T%02d:00\"".format(it % 24) }
        val temps = (0 until 48).joinToString(",") { "$it.0" }
        return """
            {"current": {"time": "$currentTime", "temperature_2m": 11.6, "weather_code": 61},
             "hourly": {"time": [$times], "temperature_2m": [$temps]}}
        """
    }

    @Test
    fun `aligns the hourly series on the current hour`() {
        val weather = parseForecast(forecastJson("2026-10-20T22:45"), city = "Lyon")

        assertEquals(12, weather.temperature)
        assertEquals(WeatherCondition.RAIN, weather.condition)
        // Current hour = index 22: the next 24 h span indices 23 to 46.
        assertEquals(23, weather.min)
        assertEquals(46, weather.max)
        assertEquals(
            listOf(HourlyForecast(1, 25), HourlyForecast(4, 28), HourlyForecast(7, 31), HourlyForecast(10, 34)),
            weather.hourly
        )
    }

    @Test
    fun `truncates forecasts at the end of the series`() {
        val weather = parseForecast(forecastJson("2026-10-21T20:10"), city = " ")

        assertEquals(null, weather.city)
        assertEquals(47, weather.max)
        assertEquals(listOf(23), weather.hourly.map { it.hour })
    }
}
