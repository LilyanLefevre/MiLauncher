package com.lilyan_lefevre.milauncher.remote

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URLDecoder

/** Just enough HTTP/1.1 for the local remote page: small requests, one response per connection. */
internal data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
    val query: Map<String, String> = emptyMap(),
)

internal class HttpResponse(val status: Int, val contentType: String, val body: ByteArray) {
    companion object {
        fun json(status: Int, json: String) = HttpResponse(status, "application/json; charset=utf-8", json.toByteArray())
        fun status(status: Int) = json(status, """{"ok":${status in 200..299}}""")
    }
}

internal const val MAX_REQUEST_BYTES = 8 * 1024

/** Parses one request, or returns null when it is malformed or larger than [MAX_REQUEST_BYTES]. */
internal fun readRequest(input: InputStream): HttpRequest? {
    val head = ByteArrayOutputStream()
    // Read byte by byte up to the blank line so the body stays in the stream;
    // the last four bytes are packed in an Int to spot "\r\n\r\n".
    var last4 = 0
    while (last4 != HEADER_END) {
        val byte = input.read()
        if (byte < 0 || head.size() >= MAX_REQUEST_BYTES) return null
        head.write(byte)
        last4 = (last4 shl 8) or byte
    }
    val lines = head.toString(Charsets.ISO_8859_1.name()).split("\r\n")
    val requestLine = lines.first().split(' ')
    if (requestLine.size != 3) return null
    val headers = lines.drop(1).filter { ':' in it }
        .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }

    val length = headers["content-length"]?.toIntOrNull() ?: 0
    if (length !in 0..MAX_REQUEST_BYTES) return null
    val body = ByteArray(length)
    var read = 0
    while (read < length) {
        val n = input.read(body, read, length - read)
        if (n < 0) return null
        read += n
    }
    val target = requestLine[1]
    val query = target.substringAfter('?', "").split('&').filter { '=' in it }
        .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
    return HttpRequest(requestLine[0], target.substringBefore('?'), headers, body.toString(Charsets.UTF_8), query)
}

internal fun writeResponse(output: OutputStream, response: HttpResponse) {
    val reason = when (response.status) {
        200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"
        429 -> "Too Many Requests"; else -> "Error"
    }
    val head = "HTTP/1.1 ${response.status} $reason\r\n" +
        "Content-Type: ${response.contentType}\r\n" +
        "Content-Length: ${response.body.size}\r\n" +
        "Cache-Control: no-store\r\n" +
        "Connection: close\r\n\r\n"
    output.write(head.toByteArray(Charsets.ISO_8859_1))
    output.write(response.body)
    output.flush()
}

/** True for loopback, link-local and private (RFC 1918, IPv6 ULA) addresses: the remote is LAN only. */
internal fun isLocalAddress(address: InetAddress): Boolean =
    address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
        (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC)

private const val HEADER_END = 0x0D0A0D0A
