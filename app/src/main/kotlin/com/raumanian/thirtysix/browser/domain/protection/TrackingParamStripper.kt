package com.raumanian.thirtysix.browser.domain.protection

import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.core.domain.model.TrackingParamRules
import java.net.URLDecoder

/**
 * Spec 023 data-model rule 4 — removes listed tracking parameters from an http/https address
 * (FR-024, FR-025). Works on the raw query string, so every kept parameter stays byte-identical
 * and in its original order; the fragment is never touched.
 */
class TrackingParamStripper(private val rules: TrackingParamRules) {

    /** Returns [url] itself (same instance) when nothing is removed. */
    fun strip(url: String): String {
        val fragmentAt = url.indexOf('#').let { if (it < 0) url.length else it }
        val queryAt = url.indexOf('?')
        if (!isWebUrl(url) || queryAt < 0 || queryAt > fragmentAt) return url

        val pairs = url.substring(queryAt + 1, fragmentAt).split('&')
        val kept = pairs.filterNot { isTracking(it) }
        return if (kept.size == pairs.size) url else rebuild(url, queryAt, fragmentAt, kept)
    }

    private fun rebuild(url: String, queryAt: Int, fragmentAt: Int, kept: List<String>): String {
        val base = url.substring(0, queryAt)
        val fragment = url.substring(fragmentAt)
        val query = kept.joinToString("&")
        return if (query.isEmpty()) base + fragment else "$base?$query$fragment"
    }

    private fun isTracking(pair: String): Boolean {
        if (pair.isEmpty()) return false
        val rawName = pair.substringBefore('=')
        return rules.matches(decode(rawName))
    }

    private fun isWebUrl(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme == UrlConstants.SCHEME_HTTP || scheme == UrlConstants.SCHEME_HTTPS
    }

    private fun decode(name: String): String =
        runCatching { URLDecoder.decode(name, Charsets.UTF_8.name()) }.getOrDefault(name)
}
