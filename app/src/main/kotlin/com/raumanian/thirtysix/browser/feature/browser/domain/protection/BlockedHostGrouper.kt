package com.raumanian.thirtysix.browser.feature.browser.domain.protection

/** The tracker hosts blocked on a page, grouped under their site. */
data class BlockedSiteGroup(val site: String, val hosts: List<String>)

/**
 * Groups [hosts] by [registrableDomain] (by site, not company: there is no licence-compatible
 * owner data). Groups are ordered by host count, most first, then by name; hosts by name.
 */
object BlockedHostGrouper {
    fun group(hosts: Collection<String>, registrableDomain: (String) -> String): List<BlockedSiteGroup> =
        hosts.distinct()
            .groupBy(registrableDomain)
            .map { (site, members) -> BlockedSiteGroup(site, members.sorted()) }
            .sortedWith(compareByDescending<BlockedSiteGroup> { it.hosts.size }.thenBy { it.site })
}
