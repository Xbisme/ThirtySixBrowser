package com.raumanian.thirtysix.browser.domain.model

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
    fun containsHostOrParent(host: String, stopAt: String): Boolean {
        var candidate = host
        while (true) {
            if (candidate in hosts) return true
            if (candidate.length <= stopAt.length) return false
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }
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
