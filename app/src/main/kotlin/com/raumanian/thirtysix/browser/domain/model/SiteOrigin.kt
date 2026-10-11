package com.raumanian.thirtysix.browser.domain.model

import java.net.URI

private const val DEFAULT_HTTPS_PORT = 443
private const val DEFAULT_HTTP_PORT = 80
private const val NO_PORT = -1

/**
 * Spec 021 data-model §1 — the site a request comes from, as `scheme://host[:port]` with the
 * scheme and host lower-cased and a default port left out. Pure Kotlin.
 */
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

    /** Whether [origin] is a secure (https) origin — the only kind that may use capabilities (FR-007). */
    fun isSecure(origin: String): Boolean = origin.startsWith("https://")

    /** What the person sees: the origin without its scheme. */
    fun display(origin: String): String = origin.substringAfter("://")
}
