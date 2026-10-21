package com.lilyan_lefevre.milauncher.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `compares versions numerically`() {
        assertTrue(isNewer("1.3", "1.2"))
        assertTrue(isNewer("1.10", "1.9"))
        assertTrue(isNewer("2.0", "1.9.9"))
        assertTrue(isNewer("1.2.1", "1.2"))
    }

    @Test
    fun `same or older versions are not updates`() {
        assertFalse(isNewer("1.2", "1.2"))
        assertFalse(isNewer("1.2.0", "1.2"))
        assertFalse(isNewer("1.1", "1.2"))
    }
}
