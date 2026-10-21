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
    fun usage(period: ScreenTimePeriod): List<AppUsage> = usageBetween(dayStart(period.days - 1), System.currentTimeMillis())
        .map { (pkg, millis) -> AppUsage(pkg, millis) }
        .sortedByDescending { it.foregroundMillis }

    /** Total time spent in [packages] for each of the last 7 days, oldest first, today last. */
    fun dailyTotals(packages: Set<String>): List<Long> {
        val now = System.currentTimeMillis()
        return (6L downTo 0L).map { daysAgo ->
            val end = if (daysAgo == 0L) now else dayStart(daysAgo - 1)
            usageBetween(dayStart(daysAgo), end).filterKeys { it in packages }.values.sum()
        }
    }

    private fun dayStart(daysAgo: Long): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Foreground time per package between [start] and [end], without this launcher. */
    private fun usageBetween(start: Long, end: Long): Map<String, Long> {
        val events = mutableListOf<UsageEvent>()
        val raw = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, end)
        val event = UsageEvents.Event()
        while (raw.getNextEvent(event)) {
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Kind.PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> UsageEvent.Kind.STOPPED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvent.Kind.SCREEN_OFF
                else -> continue
            }
            events += UsageEvent(event.packageName, event.timeStamp, kind, event.className.orEmpty())
        }
        return foregroundTime(events, start, end).filterKeys { it != context.packageName }
    }
}

/** One lifecycle event of [activity] (class name) in [packageName]; empty for screen events. */
internal data class UsageEvent(val packageName: String, val time: Long, val kind: Kind, val activity: String = "") {
    enum class Kind { RESUMED, PAUSED, STOPPED, SCREEN_OFF }
}

/**
 * Sums foreground time per package between [start] and [end]. An app is in front while at least one of its
 * activities is resumed: apps often resume their next screen in the same millisecond as they pause the previous
 * one, then stop it, so tracking packages alone would cut those intervals short.
 *
 * The activity already in front at [start] shows up as a pause before any resume and counts from [start];
 * one still in front at [end] counts until [end]; turning the screen off ends everything. A stop only closes
 * an activity: it can come hours after the pause (screen off at night).
 */
internal fun foregroundTime(events: List<UsageEvent>, start: Long, end: Long): Map<String, Long> {
    val totals = mutableMapOf<String, Long>()
    val resumed = mutableMapOf<String, MutableSet<String>>() // package -> resumed activities
    val inFrontSince = mutableMapOf<String, Long>()
    var anyEvent = false

    fun open(pkg: String, activity: String, at: Long) {
        resumed.getOrPut(pkg) { mutableSetOf() } += activity
        inFrontSince.putIfAbsent(pkg, at)
    }

    fun close(pkg: String, activity: String?, at: Long) {
        val activities = resumed[pkg] ?: return
        if (activity == null) activities.clear() else activities -= activity
        if (activities.isNotEmpty()) return
        resumed -= pkg
        val from = inFrontSince.remove(pkg) ?: return
        totals[pkg] = (totals[pkg] ?: 0L) + (at - from)
    }

    for (event in events.sortedBy { it.time }) {
        when (event.kind) {
            UsageEvent.Kind.RESUMED -> open(event.packageName, event.activity, event.time)

            UsageEvent.Kind.PAUSED -> {
                // Only what was in front when the window opened can pause before anything else happened.
                if (!anyEvent) open(event.packageName, event.activity, start)
                close(event.packageName, event.activity, event.time)
            }

            UsageEvent.Kind.STOPPED -> close(event.packageName, event.activity, event.time)

            UsageEvent.Kind.SCREEN_OFF -> resumed.keys.toList().forEach { close(it, null, event.time) }
        }
        anyEvent = true
    }
    resumed.keys.toList().forEach { close(it, null, end) }
    return totals
}
