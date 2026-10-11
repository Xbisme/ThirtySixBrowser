package com.raumanian.thirtysix.browser.testing

import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Spec 022 — a one-thread HTTP server for instrumented tests. It serves the test APK's assets
 * (`app/src/androidTest/assets/`) at `http://127.0.0.1:<port>/<name>` and records each request's
 * `User-Agent` header.
 *
 * A server is needed because the WebView's file access is off (Spec 007 FR-013), and
 * `file:///android_asset/` would read the app's assets, not the test APK's.
 */
class AssetPageServer : AutoCloseable {
    private val socket = ServerSocket(0)

    /** The `User-Agent` header of every request so far, in order. */
    val userAgents: MutableList<String> = CopyOnWriteArrayList()

    /** Every request so far as (path without query, `User-Agent`), in order. */
    val requests: MutableList<Pair<String, String>> = CopyOnWriteArrayList()

    /** The `User-Agent` of each request for the asset [name], in order. */
    fun userAgentsFor(name: String): List<String> = requests.filter { it.first == name }.map { it.second }

    init {
        thread(isDaemon = true) { serve() }
    }

    fun url(name: String): String = "http://127.0.0.1:${socket.localPort}/$name"

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
                    while (!line.isNullOrEmpty()) {
                        if (line.startsWith(USER_AGENT_HEADER, ignoreCase = true)) {
                            userAgent = line.substring(USER_AGENT_HEADER.length).trim()
                            userAgents += userAgent
                        }
                        line = reader.readLine()
                    }
                    val name = path.trimStart('/').substringBefore('?')
                    requests += name to userAgent
                    val body = runCatching { assets.open(name).use { input -> input.readBytes() } }.getOrNull()
                    val head = if (body != null) {
                        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n"
                    } else {
                        "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n"
                    }
                    it.getOutputStream().write((head + "Connection: close\r\n\r\n").toByteArray() + (body ?: ByteArray(0)))
                }
            }
        }
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent:"
    }
}
