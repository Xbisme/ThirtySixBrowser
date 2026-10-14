package com.raumanian.thirtysix.browser.domain.protection

import com.raumanian.thirtysix.browser.core.domain.model.TrackerHostSet
/**
 * Spec 023 data-model rule 3 — whether a request made by a page is blocked (FR-004, FR-004a,
 * FR-008). Called from WebView IO threads for every subresource: no I/O, no locks.
 */
class TrackerMatcher(
    private val trackers: TrackerHostSet,
    private val suffixes: PublicSuffixMatcher,
    private val neverBlock: Set<String> = NEVER_BLOCK_HOSTS,
) {
    fun shouldBlock(requestHost: String, topHost: String, protectionOn: Boolean): Boolean {
        val host = requestHost.lowercase()
        val site = suffixes.registrableDomain(host)
        return protectionOn &&
            site != suffixes.registrableDomain(topHost) &&
            !isNeverBlock(host) &&
            trackers.containsHostOrParent(host, stopAt = site)
    }

    private fun isNeverBlock(host: String): Boolean =
        neverBlock.any { host == it || host.endsWith(".$it") }

    companion object {
        /**
         * Research R3 / FR-004a — hosts serving content the person chose to see (embedded
         * video and maps, sign-in, public CDNs). Checked before the tracker list so a future
         * list refresh can never block them.
         */
        val NEVER_BLOCK_HOSTS: Set<String> = setOf(
            "accounts.google.com",
            "appleid.apple.com",
            "login.microsoftonline.com",
            "youtube.com",
            "youtube-nocookie.com",
            "googlevideo.com",
            "ytimg.com",
            "maps.googleapis.com",
            "gstatic.com",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
            "cdnjs.cloudflare.com",
            "cdn.jsdelivr.net",
            "unpkg.com",
        )
    }
}
