package com.raumanian.thirtysix.browser.core.domain.model

/**
 * Spec 023 data-model "Rule data" — the bundled protection lists after parsing. Immutable;
 * built once per process by the data layer and shared by every WebView thread.
 */
data class ProtectionData(
    val trackers: TrackerHostSet,
    val suffixes: PublicSuffixRules,
    val params: TrackingParamRules,
)

/** Lower-case tracker and ad-server hosts. A listed host also covers its subdomains. */
class TrackerHostSet(private val hosts: Set<String>) {
    val size: Int get() = hosts.size

    /**
     * True when [host] or one of its parents is listed, checking parents no shorter than
     * [stopAt] (the host's registrable domain) so a public suffix is never matched.
     */
    fun containsHostOrParent(host: String, stopAt: String): Boolean =
        generateSequence(host) { candidate ->
            candidate.substringAfter('.', missingDelimiterValue = "").takeIf { it.length >= stopAt.length }
        }.any { it in hosts }
}

/** Public Suffix List rules, lower-case, without the `*.` / `!` markers. */
class PublicSuffixRules(
    val normal: Set<String>,
    val wildcard: Set<String>,
    val exception: Set<String>,
) {
    val size: Int get() = normal.size + wildcard.size + exception.size
}

/** Tracking query-parameter names (exact, case-sensitive) and name prefixes. */
class TrackingParamRules(
    val names: Set<String>,
    val prefixes: List<String>,
) {
    fun matches(name: String): Boolean = name in names || prefixes.any { name.startsWith(it) }
}
