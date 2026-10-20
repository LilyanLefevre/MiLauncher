package com.lilyan_lefevre.milauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lilyan_lefevre.milauncher.data.Weather
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

/** Grands widgets de l'accueil : horloge et météo détaillée. */
@Composable
fun LargeWidgets(weather: Weather?, modifier: Modifier = Modifier) {
    val now by rememberCurrentMinute()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column {
            Text(now.format(TimeFormat), fontSize = 112.sp, fontWeight = FontWeight.Light, lineHeight = 112.sp)
            Text(now.format(DateFormat), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (weather != null) WeatherDetails(weather)
    }
}

/** Version réduite affichée au-dessus de la liste complète des apps. */
@Composable
fun CompactWidgets(weather: Weather?, modifier: Modifier = Modifier) {
    val now by rememberCurrentMinute()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("${now.format(TimeFormat)}  ·  ${now.format(DateFormat)}", style = MaterialTheme.typography.titleLarge)
        if (weather != null) {
            Text(
                listOfNotNull("${weather.temperature}°", weather.description).joinToString("  ·  "),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun WeatherDetails(weather: Weather) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.End) {
        Text("${weather.temperature}°", fontSize = 72.sp, fontWeight = FontWeight.Light, lineHeight = 72.sp)
        Text(
            listOfNotNull(weather.description, weather.city).joinToString(" · "),
            style = MaterialTheme.typography.titleLarge,
            color = dim,
        )
        Text(
            "Min ${weather.min}° · Max ${weather.max}° sur 24 h",
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 12.dp)) {
            weather.hourly.forEach {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it.hour, style = MaterialTheme.typography.bodyMedium, color = dim)
                    Text("${it.temperature}°", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Heure courante, mise à jour au début de chaque minute. */
@Composable
private fun rememberCurrentMinute(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        delay(now.until(now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1), ChronoUnit.MILLIS))
    }
}
