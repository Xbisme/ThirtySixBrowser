package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import java.net.URI
import java.net.URISyntaxException

/** The Referer a download sends: nothing, the page origin, or the full page address on the same origin. */
object DownloadReferrer {
    private const val HTTP = "http"
    private const val HTTPS = "https"
    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
    private const val NO_PORT = -1

    fun of(pageUrl: String?, downloadUrl: String, isIncognito: Boolean): String? {
        val page = pageUrl?.let(::parse)?.takeIf { !isIncognito && it.isWeb() } ?: return null
        val download = parse(downloadUrl)
        val downgrade = page.scheme.equals(HTTPS, ignoreCase = true) &&
            download?.scheme.equals(HTTP, ignoreCase = true)
        return when {
            downgrade -> null
            download != null && sameOrigin(page, download) -> pageUrl.substringBefore('#')
            else -> origin(page)
        }
    }

    /** Like [of], but never the full address: the download service may follow redirects to another site. */
    fun originOnly(pageUrl: String?, downloadUrl: String, isIncognito: Boolean): String? =
        of(pageUrl, downloadUrl, isIncognito)?.let { pageUrl?.let(::parse)?.let(::origin) }

    private fun parse(url: String): URI? = try {
        URI(url)
    } catch (_: URISyntaxException) {
        null
    }

    private fun URI.isWeb(): Boolean =
        host != null && (scheme.equals(HTTP, ignoreCase = true) || scheme.equals(HTTPS, ignoreCase = true))

    private fun sameOrigin(a: URI, b: URI): Boolean =
        a.scheme.equals(b.scheme, ignoreCase = true) &&
            a.host.equals(b.host, ignoreCase = true) &&
            effectivePort(a) == effectivePort(b)

    private fun origin(uri: URI): String {
        val port = if (uri.port == NO_PORT) "" else ":${uri.port}"
        return "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port/"
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != NO_PORT -> uri.port
        uri.scheme.equals(HTTPS, ignoreCase = true) -> HTTPS_PORT
        else -> HTTP_PORT
    }
}
