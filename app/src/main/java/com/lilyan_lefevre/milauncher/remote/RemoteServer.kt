package com.lilyan_lefevre.milauncher.remote

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lilyan_lefevre.milauncher.MainActivity
import com.lilyan_lefevre.milauncher.data.AppRepository
import com.lilyan_lefevre.milauncher.data.LauncherPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Local-network remote for the phone: serves a page and a small JSON API to list and launch apps,
 * open a link on the TV and go back home. Every API call needs the pairing code shown on the TV.
 */
class RemoteServer(private val context: Context) {

    private val apps = AppRepository(context)
    private val prefs = LauncherPrefs(context)
    private val guard = PinGuard()
    private var socket: ServerSocket? = null

    val isRunning: Boolean
        @Synchronized get() = socket != null

    // ponytail: one thread per connection, fine for a handful of phones on the LAN.
    @Synchronized
    fun start() {
        if (socket != null) return
        val server = ServerSocket().apply { reuseAddress = true }.also { socket = it }
        thread(name = "remote-server", isDaemon = true) {
            // Right after an update the previous process can still hold the port for a moment.
            val bound = (1..BIND_ATTEMPTS).any {
                !server.isClosed && (runCatching { server.bind(InetSocketAddress(PORT)) }.isSuccess || retryLater())
            }
            if (!bound) {
                synchronized(this) { if (socket === server) socket = null }
                server.close()
                return@thread
            }
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { client.use(::serve) }
            }
        }
    }

    private fun retryLater(): Boolean {
        Thread.sleep(1_000)
        return false
    }

    @Synchronized
    fun stop() {
        socket?.close()
        socket = null
    }

    private fun serve(client: Socket) {
        client.soTimeout = 5_000
        val output = client.getOutputStream()
        if (!isLocalAddress(client.inetAddress)) return writeResponse(output, HttpResponse.status(403))
        val request = readRequest(client.getInputStream()) ?: return writeResponse(output, HttpResponse.status(400))
        writeResponse(output, runCatching { handle(request) }.getOrElse { HttpResponse.status(400) })
    }

    private fun handle(request: HttpRequest): HttpResponse {
        // The page and its web app files hold no data, so they are served without the code.
        STATIC_FILES[request.path]?.takeIf { request.method == "GET" }?.let { (asset, type) ->
            return HttpResponse(200, type, context.assets.open(asset).use { it.readBytes() })
        }
        when (guard.check(request.headers["x-pin"], prefs.remotePin)) {
            PinGuard.Result.ACCEPTED -> Unit
            PinGuard.Result.REJECTED -> return HttpResponse.status(401)
            PinGuard.Result.LOCKED -> return HttpResponse.status(429)
        }
        val visible = apps.labels() - prefs.hidden
        return when ("${request.method} ${request.path}") {
            "GET /api/apps" -> {
                val list = JSONArray()
                visible.entries.sortedBy { it.value.lowercase() }
                    .forEach { list.put(JSONObject().put("package", it.key).put("label", it.value)) }
                HttpResponse.json(200, list.toString())
            }
            "POST /api/launch" -> {
                val pkg = JSONObject(request.body).getString("package")
                // Only apps the launcher shows: the API cannot start arbitrary components.
                if (pkg !in visible) return HttpResponse.status(404)
                apps.launch(pkg)
                HttpResponse.status(200)
            }
            "POST /api/open" -> {
                val url = JSONObject(request.body).getString("url").trim()
                if (!url.startsWith("https://") && !url.startsWith("http://")) return HttpResponse.status(400)
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                HttpResponse.status(200)
            }
            "POST /api/home" -> {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                HttpResponse.status(200)
            }
            else -> HttpResponse.status(404)
        }
    }

    companion object {
        const val PORT = 8765
        private const val BIND_ATTEMPTS = 10

        /** Request path to (asset file, content type). */
        private val STATIC_FILES = mapOf(
            "/" to ("remote.html" to "text/html; charset=utf-8"),
            "/manifest.webmanifest" to ("manifest.webmanifest" to "application/manifest+json"),
            "/icon-192.png" to ("icon-192.png" to "image/png"),
            "/icon-512.png" to ("icon-512.png" to "image/png"),
            "/apple-touch-icon.png" to ("apple-touch-icon.png" to "image/png"),
        )
    }
}
