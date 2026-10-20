package com.lilyan_lefevre.milauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherParsingTest {

    /** 48 h de températures : 0°, 1°, 2°… pour lire facilement les index. */
    private fun forecastJson(currentTime: String): String {
        val times = (0 until 48).joinToString(",") { "\"2026-10-${20 + it / 24}T%02d:00\"".format(it % 24) }
        val temps = (0 until 48).joinToString(",") { "$it.0" }
        return """
            {"current": {"time": "$currentTime", "temperature_2m": 11.6, "weather_code": 61},
             "hourly": {"time": [$times], "temperature_2m": [$temps]}}
        """
    }

    @Test
    fun `aligne la série horaire sur l'heure courante`() {
        val weather = parseForecast(forecastJson("2026-10-20T22:45"), city = "Lyon")

        assertEquals(12, weather.temperature)
        assertEquals("Pluie", weather.description)
        // Heure courante = index 22 : les 24 h suivantes vont de 23 à 46.
        assertEquals(23, weather.min)
        assertEquals(46, weather.max)
        assertEquals(
            listOf(HourlyForecast("01h", 25), HourlyForecast("04h", 28), HourlyForecast("07h", 31), HourlyForecast("10h", 34)),
            weather.hourly,
        )
    }

    @Test
    fun `tronque les prévisions en fin de série`() {
        val weather = parseForecast(forecastJson("2026-10-21T20:10"), city = " ")

        assertEquals(null, weather.city)
        assertEquals(47, weather.max)
        assertEquals(listOf("23h"), weather.hourly.map { it.hour })
    }
}
