package com.lilyan_lefevre.milauncher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.math.roundToInt

data class Weather(
    val temperature: Int,
    val description: String?,
    val city: String?,
    val min: Int,
    val max: Int,
    val hourly: List<HourlyForecast>,
)

data class HourlyForecast(val hour: String, val temperature: Int)

/** Météo Open-Meteo (sans clé) pour une position approximative obtenue par IP. */
class WeatherRepository(context: Context) {

    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** Null si le réseau n'est pas disponible. */
    suspend fun fetch(): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            // Pas de permission de localisation sur TV : position par IP, mise en cache.
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

/** Extrait la météo actuelle, le min/max des 24 h à venir et 4 prévisions espacées de 3 h. */
internal fun parseForecast(json: String, city: String?): Weather {
    val root = JSONObject(json)
    val current = root.getJSONObject("current")
    val times = root.getJSONObject("hourly").getJSONArray("time")
    val temps = root.getJSONObject("hourly").getJSONArray("temperature_2m")
    fun temp(i: Int) = temps.getDouble(i).roundToInt()

    // "2026-10-20T22:45" correspond à l'entrée horaire "2026-10-20T22:00".
    val currentHour = current.getString("time").take(13) + ":00"
    val now = (0 until times.length()).first { times.getString(it) == currentHour }
    val next24h = (now + 1..minOf(now + 24, temps.length() - 1)).map(::temp)

    return Weather(
        temperature = current.getDouble("temperature_2m").roundToInt(),
        description = describe(current.getInt("weather_code")),
        city = city?.ifBlank { null },
        min = next24h.min(),
        max = next24h.max(),
        hourly = (1..4).map { now + it * 3 }.filter { it < temps.length() }
            .map { HourlyForecast(hour = times.getString(it).substring(11, 13) + "h", temperature = temp(it)) },
    )
}

/** Codes WMO : https://open-meteo.com/en/docs */
private fun describe(code: Int): String? = when (code) {
    0 -> "Ciel dégagé"
    1, 2 -> "Éclaircies"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    in 51..57 -> "Bruine"
    in 61..67, in 80..82 -> "Pluie"
    in 71..77, 85, 86 -> "Neige"
    in 95..99 -> "Orage"
    else -> null
}
