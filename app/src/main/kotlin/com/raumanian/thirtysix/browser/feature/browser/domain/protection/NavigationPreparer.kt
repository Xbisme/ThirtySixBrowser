package com.raumanian.thirtysix.browser.feature.browser.domain.protection

/**
 * What the app actually loads for an address: tracking parameters removed, then upgraded to
 * `https://` when possible, unless the site is in the "without protection" list.
 * Idempotent: `prepare(prepare(u).url) == prepare(u).url`.
 */
class NavigationPreparer(
    private val stripper: TrackingParamStripper,
    private val suffixes: PublicSuffixMatcher,
    private val isSiteProtected: (site: String, isIncognito: Boolean) -> Boolean,
    private val failedHosts: () -> Set<String>,
) {
    data class Prepared(
        val url: String,
        /** The stripped `http://` address when [url] is its HTTPS upgrade. */
        val upgradedFrom: String?,
    )

    fun prepare(url: String, isIncognito: Boolean): Prepared {
        val host = HttpsUpgradePolicy.hostOf(url)
        if (host == null || !isSiteProtected(suffixes.registrableDomain(host), isIncognito)) {
            return Prepared(url, upgradedFrom = null)
        }
        val stripped = stripper.strip(url)
        val upgraded = HttpsUpgradePolicy.upgrade(stripped, failedHosts())
        return if (upgraded != null) Prepared(upgraded, upgradedFrom = stripped) else Prepared(stripped, null)
    }
}
