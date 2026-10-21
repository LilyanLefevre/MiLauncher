package com.lilyan_lefevre.milauncher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

enum class ScreenTimePeriod(val days: Long) { TODAY(1), LAST_7_DAYS(7) }

data class AppUsage(val packageName: String, val foregroundMillis: Long)

/** Time spent in each app, computed on the device from the system usage events. */
class ScreenTimeRepository(private val context: Context) {

    /** Usage over [period], longest first, without this launcher. Empty until usage access is granted. */
    fun usage(period: ScreenTimePeriod): List<AppUsage> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).minusDays(period.days - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()

        val events = mutableListOf<UsageEvent>()
        val raw = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, end)
        val event = UsageEvents.Event()
        while (raw.getNextEvent(event)) {
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEvent.Kind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvent.Kind.SCREEN_OFF
                else -> continue
            }
            events += UsageEvent(event.packageName, event.timeStamp, kind)
        }

        return foregroundTime(events, start, end)
            .filterKeys { it != context.packageName }
            .map { (pkg, millis) -> AppUsage(pkg, millis) }
            .sortedByDescending { it.foregroundMillis }
    }
}

internal data class UsageEvent(val packageName: String, val time: Long, val kind: Kind) {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF }
}

/**
 * Sums foreground intervals per package between [start] and [end]. An app already in the
 * foreground at [start] counts from [start]; one still in the foreground at [end] counts until [end];
 * turning the screen off closes every open interval.
 */
internal fun foregroundTime(events: List<UsageEvent>, start: Long, end: Long): Map<String, Long> {
    val totals = mutableMapOf<String, Long>()
    val openSince = mutableMapOf<String, Long>()
    val seen = mutableSetOf<String>()

    fun close(pkg: String, at: Long) {
        val from = openSince.remove(pkg) ?: return
        totals[pkg] = (totals[pkg] ?: 0L) + (at - from)
    }

    for (event in events.sortedBy { it.time }) {
        when (event.kind) {
            UsageEvent.Kind.RESUMED -> openSince.putIfAbsent(event.packageName, event.time)

            UsageEvent.Kind.PAUSED -> {
                // A pause before any resume means the app was already in front when the window opened.
                if (event.packageName !in seen && event.packageName !in openSince) openSince[event.packageName] = start
                close(event.packageName, event.time)
            }

            UsageEvent.Kind.SCREEN_OFF -> openSince.keys.toList().forEach { close(it, event.time) }
        }
        if (event.kind != UsageEvent.Kind.SCREEN_OFF) seen += event.packageName
    }
    openSince.keys.toList().forEach { close(it, end) }
    return totals
}
