package com.lilyan_lefevre.milauncher.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dominant vivid color of an image, as ARGB, or null when it is mostly grey, black or white.
 * Each pixel weighs by its saturation, so a colored logo on a white banner still gives the logo color.
 */
internal fun accentColor(pixels: IntArray): Int? {
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var weight = 0.0
    for (pixel in pixels) {
        if (pixel ushr 24 < 128) continue // transparent
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        if (high < 40) continue // near black
        val saturation = (high - low) / high.toDouble()
        val w = saturation * saturation
        red += r * w
        green += g * w
        blue += b * w
        weight += w
    }
    // Too little color overall: let the caller fall back to the theme accent.
    if (weight < pixels.size * 0.02) return null
    fun channel(sum: Double) = (sum / weight).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}
