package com.lilyan_lefevre.milauncher.remote

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTest {

    @Test
    fun `parses a request with headers and body`() {
        val raw = "POST /api/launch?x=1 HTTP/1.1\r\nHost: tv\r\nX-Pin: 123456\r\nContent-Length: 13\r\n\r\n{\"package\":1}"
        val request = readRequest(raw.byteInputStream())!!
        assertEquals("POST", request.method)
        assertEquals("/api/launch", request.path)
        assertEquals("123456", request.headers["x-pin"])
        assertEquals("{\"package\":1}", request.body)
    }

    @Test
    fun `rejects malformed or oversized requests`() {
        assertNull(readRequest("garbage\r\n\r\n".byteInputStream()))
        assertNull(readRequest("GET / HTTP/1.1\r\nContent-Length: 999999\r\n\r\n".byteInputStream()))
        assertNull(readRequest(("GET / HTTP/1.1\r\nX: " + "a".repeat(MAX_REQUEST_BYTES)).byteInputStream()))
    }

    @Test
    fun `only local network clients are allowed`() {
        listOf("127.0.0.1", "192.168.1.20", "10.0.0.5", "172.16.3.4", "fe80::1", "fd12::1").forEach {
            assertTrue(it, isLocalAddress(InetAddress.getByName(it)))
        }
        listOf("8.8.8.8", "2001:4860:4860::8888").forEach {
            assertFalse(it, isLocalAddress(InetAddress.getByName(it)))
        }
    }

    @Test
    fun `locks out after too many wrong codes`() {
        var time = 0L
        val guard = PinGuard(maxFailures = 3, lockMillis = 1_000, now = { time })
        assertEquals(PinGuard.Result.ACCEPTED, guard.check("4242", "4242"))
        repeat(3) { assertEquals(PinGuard.Result.REJECTED, guard.check("0000", "4242")) }
        // Even the right code is refused while locked.
        assertEquals(PinGuard.Result.LOCKED, guard.check("4242", "4242"))
        time = 1_000
        assertEquals(PinGuard.Result.ACCEPTED, guard.check("4242", "4242"))
        assertEquals(PinGuard.Result.REJECTED, guard.check(null, "4242"))
    }
}
