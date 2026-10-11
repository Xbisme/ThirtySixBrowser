package com.raumanian.thirtysix.browser.domain.protection

/**
 * Spec 023 data-model rule 6 — what the app actually loads for an address the person (or a
 * page) navigates to: tracking parameters removed, then upgraded to `https://` when possible,
 * unless the destination site is in the person's "without protection" list (FR-016, FR-018,
 * FR-024). Idempotent: `prepare(prepare(u).url) == prepare(u).url`.
 */
class NavigationPreparer(
    private val stripper: TrackingParamStripper,
    private val suffixes: PublicSuffixMatcher,
    private val isSiteProtected: (site: String, isIncognito: Boolean) -> Boolean,
    private val failedHosts: () -> Set<String>,
) {
    /** @property upgradedFrom the stripped `http://` address when [url] is its HTTPS upgrade. */
    data class Prepared(val url: String, val upgradedFrom: String?)

    fun prepare(url: String, isIncognito: Boolean): Prepared {
        val host = HttpsUpgradePolicy.hostOf(url) ?: return Prepared(url, upgradedFrom = null)
        if (!isSiteProtected(suffixes.registrableDomain(host), isIncognito)) {
            return Prepared(url, upgradedFrom = null)
        }
        val stripped = stripper.strip(url)
        val upgraded = HttpsUpgradePolicy.upgrade(stripped, failedHosts())
        return if (upgraded != null) Prepared(upgraded, upgradedFrom = stripped) else Prepared(stripped, null)
    }
}
