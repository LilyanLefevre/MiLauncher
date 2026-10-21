package com.lilyan_lefevre.milauncher.ui

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lilyan_lefevre.milauncher.R
import com.lilyan_lefevre.milauncher.data.Weather
import com.lilyan_lefevre.milauncher.data.WeatherCondition
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/** Large home-screen widgets: clock and detailed weather. */
@Composable
fun LargeWidgets(weather: Weather?, modifier: Modifier = Modifier) {
    val now by rememberCurrentMinute()
    val formats = rememberClockFormats()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column {
            Text(now.format(formats.time), fontSize = 112.sp, fontWeight = FontWeight.Light, lineHeight = 112.sp)
            Text(
                now.format(formats.date),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (weather != null) WeatherDetails(weather, formats)
    }
}

/** Compact version shown above the full app list. */
@Composable
fun CompactWidgets(weather: Weather?, modifier: Modifier = Modifier) {
    val now by rememberCurrentMinute()
    val formats = rememberClockFormats()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("${now.format(formats.time)}  ·  ${now.format(formats.date)}", style = MaterialTheme.typography.titleLarge)
        if (weather != null) {
            Text(
                listOfNotNull(
                    stringResource(R.string.temperature, weather.temperature),
                    weather.condition?.let {
                        stringResource(it.label)
                    }
                )
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun WeatherDetails(weather: Weather, formats: ClockFormats) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.End) {
        Text(stringResource(R.string.temperature, weather.temperature), fontSize = 72.sp, fontWeight = FontWeight.Light, lineHeight = 72.sp)
        Text(
            listOfNotNull(weather.condition?.let { stringResource(it.label) }, weather.city).joinToString(" · "),
            style = MaterialTheme.typography.titleLarge,
            color = dim
        )
        Text(
            stringResource(R.string.min_max_24h, weather.min, weather.max),
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 12.dp)) {
            weather.hourly.forEach {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(LocalTime.of(it.hour, 0).format(formats.hour), style = MaterialTheme.typography.bodyMedium, color = dim)
                    Text(stringResource(R.string.temperature, it.temperature), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Current time, updated at the start of every minute. */
@Composable
private fun rememberCurrentMinute(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        delay(now.until(now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1), ChronoUnit.MILLIS))
    }
}

/** Locale-aware formatters; time follows the system 12/24-hour setting. */
private class ClockFormats(val time: DateTimeFormatter, val date: DateTimeFormatter, val hour: DateTimeFormatter)

@Composable
private fun rememberClockFormats(): ClockFormats {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) {
        fun formatter(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        ClockFormats(
            time = formatter(if (is24Hour) "Hm" else "hm"),
            date = formatter("EEEEdMMMM"),
            hour = formatter(if (is24Hour) "H" else "ha")
        )
    }
}

private val WeatherCondition.label: Int
    @StringRes get() = when (this) {
        WeatherCondition.CLEAR -> R.string.weather_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherCondition.OVERCAST -> R.string.weather_overcast
        WeatherCondition.FOG -> R.string.weather_fog
        WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    }
