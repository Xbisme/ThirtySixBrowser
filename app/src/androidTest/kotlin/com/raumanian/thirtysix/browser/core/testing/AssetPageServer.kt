package com.raumanian.thirtysix.browser.core.testing

import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A one-thread HTTP server serving the test APK's assets at `http://127.0.0.1:<port>/<name>`.
 * Needed because the WebView's file access is off, and `file:///android_asset/` would read the
 * app's assets, not the test APK's.
 */
class AssetPageServer : AutoCloseable {
    private val socket = ServerSocket(0)

    /** The `User-Agent` header of every request so far, in order. */
    val userAgents: MutableList<String> = CopyOnWriteArrayList()

    /** Every request so far as (path without query, `User-Agent`), in order. */
    val requests: MutableList<Pair<String, String>> = CopyOnWriteArrayList()

    /** Every request so far: raw path with its query, and its headers (lower-case names). */
    val fullRequests: MutableList<Pair<String, Map<String, String>>> = CopyOnWriteArrayList()

    /** The raw paths (with query) of every request so far. */
    fun paths(): List<String> = fullRequests.map { it.first }

    /** The headers of each request whose path (without query) is [name]. */
    fun headersFor(name: String): List<Map<String, String>> =
        fullRequests.filter { it.first.trimStart('/').substringBefore('?') == name }.map { it.second }

    /** The `User-Agent` of each request for the asset [name], in order. */
    fun userAgentsFor(name: String): List<String> = requests.filter { it.first == name }.map { it.second }

    init {
        thread(isDaemon = true) { serve() }
    }

    fun url(name: String): String = "http://127.0.0.1:${socket.localPort}/$name"

    /** The same server under another host name (a different site to the matcher). */
    fun url(name: String, host: String): String = "http://$host:${socket.localPort}/$name"

    val port: Int get() = socket.localPort

    override fun close() = socket.close()

    private fun serve() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: return
            runCatching {
                client.use {
                    val reader = it.getInputStream().bufferedReader()
                    val path = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                    var line = reader.readLine()
                    var userAgent = ""
                    val headers = mutableMapOf<String, String>()
                    while (!line.isNullOrEmpty()) {
                        line.split(':', limit = 2).takeIf { parts -> parts.size == 2 }?.let { parts ->
                            headers[parts[0].trim().lowercase()] = parts[1].trim()
                        }
                        if (line.startsWith(USER_AGENT_HEADER, ignoreCase = true)) {
                            userAgent = line.substring(USER_AGENT_HEADER.length).trim()
                            userAgents += userAgent
                        }
                        line = reader.readLine()
                    }
                    val name = path.trimStart('/').substringBefore('?')
                    requests += name to userAgent
                    fullRequests += path to headers
                    val body = runCatching { assets.open(name).use { input -> input.readBytes() } }.getOrNull()
                    val head = if (body != null) {
                        "HTTP/1.1 200 OK\r\nContent-Type: ${contentTypeOf(name)}\r\nContent-Length: ${body.size}\r\n" +
                            extraHeadersFor(path)
                    } else {
                        "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n"
                    }
                    val response = (head + "Connection: close\r\n\r\n").toByteArray()
                    it.getOutputStream().write(response + (body ?: ByteArray(0)))
                }
            }
        }
    }

    /** A service worker must be served as JavaScript, or registration fails. */
    private fun contentTypeOf(name: String): String =
        if (name.endsWith(".js")) "application/javascript; charset=utf-8" else "text/html; charset=utf-8"

    /** `storage.html` (not `?check`) also sets a persistent HttpOnly cookie. */
    private fun extraHeadersFor(path: String): String =
        if (path.substringBefore('?').endsWith(STORAGE_FIXTURE) && !path.contains("check")) {
            "Set-Cookie: $PERSISTENT_COOKIE\r\nCache-Control: no-store\r\n"
        } else {
            "Cache-Control: no-store\r\n"
        }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent:"
        const val STORAGE_FIXTURE = "storage.html"
        const val PERSISTENT_COOKIE = "fp_persist=1; Max-Age=31536000; Path=/; HttpOnly; SameSite=Lax"
    }
}
