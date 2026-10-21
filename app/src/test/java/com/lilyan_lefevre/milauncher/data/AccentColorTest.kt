package com.lilyan_lefevre.milauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentColorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFE50914.toInt()

    @Test
    fun `a colored logo on a white banner gives the logo color`() {
        val pixels = IntArray(100) { if (it < 20) red else white }
        assertEquals(red, accentColor(pixels))
    }

    @Test
    fun `grey, black, white and transparent images have no accent`() {
        assertNull(accentColor(IntArray(100) { white }))
        assertNull(accentColor(IntArray(100) { 0xFF202020.toInt() }))
        assertNull(accentColor(IntArray(100) { 0xFF808080.toInt() }))
        assertNull(accentColor(IntArray(100) { 0x00E50914 }))
    }
}
