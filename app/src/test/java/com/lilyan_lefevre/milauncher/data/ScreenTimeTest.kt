package com.lilyan_lefevre.milauncher.data

import com.lilyan_lefevre.milauncher.data.UsageEvent.Kind.PAUSED
import com.lilyan_lefevre.milauncher.data.UsageEvent.Kind.RESUMED
import com.lilyan_lefevre.milauncher.data.UsageEvent.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeTest {

    @Test
    fun `sums each foreground interval per app`() {
        val events = listOf(
            UsageEvent("tv", 10, RESUMED), UsageEvent("tv", 40, PAUSED),
            UsageEvent("music", 40, RESUMED), UsageEvent("music", 50, PAUSED),
            UsageEvent("tv", 60, RESUMED), UsageEvent("tv", 70, PAUSED),
        )
        assertEquals(mapOf("tv" to 40L, "music" to 10L), foregroundTime(events, start = 0, end = 100))
    }

    @Test
    fun `clips intervals to the window and stops them when the screen turns off`() {
        val events = listOf(
            // Already in front when the window opens: counted from the start.
            UsageEvent("tv", 20, PAUSED),
            UsageEvent("music", 30, RESUMED),
            UsageEvent("", 50, SCREEN_OFF),
            // Still in front at the end: counted until the end.
            UsageEvent("tv", 80, RESUMED),
        )
        assertEquals(mapOf("tv" to 40L, "music" to 20L), foregroundTime(events, start = 0, end = 100))
    }

    @Test
    fun `switching activities inside one app does not double count`() {
        val events = listOf(
            UsageEvent("tv", 0, RESUMED), UsageEvent("tv", 10, RESUMED), UsageEvent("tv", 30, PAUSED),
        )
        assertEquals(mapOf("tv" to 30L), foregroundTime(events, start = 0, end = 100))
    }
}
