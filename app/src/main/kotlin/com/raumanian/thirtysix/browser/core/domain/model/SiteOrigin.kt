package com.raumanian.thirtysix.browser.core.domain.model

import java.net.URI

private const val DEFAULT_HTTPS_PORT = 443
private const val DEFAULT_HTTP_PORT = 80
private const val NO_PORT = -1

/** An origin as `scheme://host[:port]`, lower-cased, with a default port left out. */
object SiteOrigin {

    /** The normalised origin of [url], or `null` when it has no scheme or host. */
    fun of(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host?.lowercase()
        if (uri == null || scheme == null || host == null) return null
        val port = uri.port
        val isDefault = port == NO_PORT ||
            (scheme == "https" && port == DEFAULT_HTTPS_PORT) ||
            (scheme == "http" && port == DEFAULT_HTTP_PORT)
        return if (isDefault) "$scheme://$host" else "$scheme://$host:$port"
    }

    /** Only secure origins may use camera, microphone or location. */
    fun isSecure(origin: String): Boolean = origin.startsWith("https://")

    fun display(origin: String): String = origin.substringAfter("://")
}
