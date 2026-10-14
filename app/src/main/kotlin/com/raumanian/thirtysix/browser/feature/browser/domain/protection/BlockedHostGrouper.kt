package com.raumanian.thirtysix.browser.feature.browser.domain.protection

/** Spec 024 FR-016 — the tracker hosts blocked on a page, grouped under their site. */
data class BlockedSiteGroup(val site: String, val hosts: List<String>)

/**
 * Spec 024 FR-016 (clarified: by site, not company — Spec 023 research R2) — groups [hosts] by
 * [registrableDomain]. Groups are ordered by host count, most first, then by name; hosts inside a
 * group by name. Pure Kotlin.
 */
object BlockedHostGrouper {
    fun group(hosts: Collection<String>, registrableDomain: (String) -> String): List<BlockedSiteGroup> =
        hosts.distinct()
            .groupBy(registrableDomain)
            .map { (site, members) -> BlockedSiteGroup(site, members.sorted()) }
            .sortedWith(compareByDescending<BlockedSiteGroup> { it.hosts.size }.thenBy { it.site })
}
