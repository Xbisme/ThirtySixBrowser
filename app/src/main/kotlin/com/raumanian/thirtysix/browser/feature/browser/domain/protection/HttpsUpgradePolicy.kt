package com.raumanian.thirtysix.browser.feature.browser.domain.protection

import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
/**
 * The `https://` form of an `http://` address, or `null` when it must not be upgraded. Host,
 * port, path, query and fragment are kept exactly as written.
 */
object HttpsUpgradePolicy {

    fun upgrade(url: String, failedHosts: Set<String>): String? {
        val schemeEnd = url.indexOf(SCHEME_SEPARATOR)
        val isHttp = schemeEnd > 0 && url.substring(0, schemeEnd).equals(UrlConstants.SCHEME_HTTP, ignoreCase = true)
        val host = if (isHttp) hostOf(url, schemeEnd + SCHEME_SEPARATOR.length) else null
        val upgradable = host != null && isUpgradableHost(host) && host !in failedHosts
        return if (upgradable) UrlConstants.SCHEME_HTTPS + url.substring(schemeEnd) else null
    }

    /** Lower-case host of an http(s) address, or `null` when it has none. */
    fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        return hostOf(url, schemeEnd + SCHEME_SEPARATOR.length)
    }

    private fun hostOf(url: String, authorityStart: Int): String? {
        val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .let { if (it < 0) url.length else it }
        val authority = url.substring(authorityStart, authorityEnd).substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase().takeIf { it.isNotEmpty() }
    }

    private fun isUpgradableHost(host: String): Boolean =
        host != LOCALHOST && '.' in host && !host.startsWith("[") && !IPV4.matches(host)

    private const val SCHEME_SEPARATOR = "://"
    private const val LOCALHOST = "localhost"
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
}
