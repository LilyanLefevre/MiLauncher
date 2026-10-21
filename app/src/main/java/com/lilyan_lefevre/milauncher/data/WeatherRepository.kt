package com.lilyan_lefevre.milauncher.data

import android.content.Context
import androidx.core.content.edit
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Weather(
    val temperature: Int,
    val condition: WeatherCondition?,
    val city: String?,
    val min: Int,
    val max: Int,
    val hourly: List<HourlyForecast>
)

/** [hour] is the local hour of day, 0 to 23. */
data class HourlyForecast(val hour: Int, val temperature: Int)

enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM }

/** Open-Meteo weather (no API key) for an approximate IP-based location. */
class WeatherRepository(context: Context) {

    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** Null when the network is unavailable. */
    suspend fun fetch(): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            // No location permission on TV: IP-based position, cached.
            if (!prefs.contains(KEY_LAT)) {
                val geo = JSONObject(URL("https://ipwho.is/").readText())
                prefs.edit {
                    putFloat(KEY_LAT, geo.getDouble("latitude").toFloat())
                    putFloat(KEY_LON, geo.getDouble("longitude").toFloat())
                    putString(KEY_CITY, geo.optString("city"))
                }
            }
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${prefs.getFloat(KEY_LAT, 0f)}&longitude=${prefs.getFloat(KEY_LON, 0f)}" +
                "&current=temperature_2m,weather_code&hourly=temperature_2m&timezone=auto&forecast_days=2"
            parseForecast(URL(url).readText(), prefs.getString(KEY_CITY, null))
        }.getOrNull()
    }

    private companion object {
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_CITY = "city"
    }
}

/** Extracts current weather, the min/max over the next 24 h and 4 forecasts 3 h apart. */
internal fun parseForecast(json: String, city: String?): Weather {
    val root = JSONObject(json)
    val current = root.getJSONObject("current")
    val times = root.getJSONObject("hourly").getJSONArray("time")
    val temps = root.getJSONObject("hourly").getJSONArray("temperature_2m")
    fun temp(i: Int) = temps.getDouble(i).roundToInt()

    // "2026-10-20T22:45" maps to the hourly entry "2026-10-20T22:00".
    val currentHour = current.getString("time").take(13) + ":00"
    val now = (0 until times.length()).first { times.getString(it) == currentHour }
    val next24h = (now + 1..minOf(now + 24, temps.length() - 1)).map(::temp)

    return Weather(
        temperature = current.getDouble("temperature_2m").roundToInt(),
        condition = conditionOf(current.getInt("weather_code")),
        city = city?.ifBlank { null },
        min = next24h.min(),
        max = next24h.max(),
        hourly = (1..4).map { now + it * 3 }.filter { it < temps.length() }
            .map { HourlyForecast(hour = times.getString(it).substring(11, 13).toInt(), temperature = temp(it)) }
    )
}

/** WMO weather codes: https://open-meteo.com/en/docs */
private fun conditionOf(code: Int): WeatherCondition? = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.OVERCAST
    45, 48 -> WeatherCondition.FOG
    in 51..57 -> WeatherCondition.DRIZZLE
    in 61..67, in 80..82 -> WeatherCondition.RAIN
    in 71..77, 85, 86 -> WeatherCondition.SNOW
    in 95..99 -> WeatherCondition.THUNDERSTORM
    else -> null
}
