package com.raumanian.thirtysix.browser.core.testing

import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

/** An HTTPS server on `127.0.0.1` with a self-signed certificate, which the WebView must refuse. */
class SelfSignedServer : AutoCloseable {
    private val socket: SSLServerSocket = sslContext().serverSocketFactory.createServerSocket(0) as SSLServerSocket

    val url: String = "https://127.0.0.1:${socket.localPort}/"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return@thread
                runCatching {
                    client.use {
                        it.getOutputStream().write(RESPONSE.toByteArray())
                    }
                }
            }
        }
    }

    override fun close() = socket.close()

    private fun sslContext(): SSLContext {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
            assets.open(KEYSTORE_ASSET).use { load(it, PASSWORD) }
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, PASSWORD)
        }
        return SSLContext.getInstance(PROTOCOL).apply { init(keys.keyManagers, null, null) }
    }

    private companion object {
        const val KEYSTORE_ASSET = "selfsigned.p12"
        const val KEYSTORE_TYPE = "PKCS12"
        const val PROTOCOL = "TLS"
        val PASSWORD = "testpass".toCharArray()
        const val BODY = "<html><body>secret</body></html>"
        const val RESPONSE = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n" +
            "Content-Length: ${BODY.length}\r\nConnection: close\r\n\r\n$BODY"
    }
}
