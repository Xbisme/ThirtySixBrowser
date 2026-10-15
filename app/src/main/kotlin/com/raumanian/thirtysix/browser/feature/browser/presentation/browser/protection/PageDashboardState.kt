package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import com.raumanian.thirtysix.browser.core.domain.usecase.IsSiteFireproofUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.feature.browser.domain.protection.BlockedHostGrouper
import com.raumanian.thirtysix.browser.feature.browser.domain.protection.BlockedSiteGroup
/**
 * What the privacy dashboard shows for the page on screen. There is no "cookie banner declined"
 * Line because nothing comes back from the page.
 */
data class PageDashboardState(
    val site: String,
    val connectionSecure: Boolean,
    val protectionOn: Boolean,
    val blockedGroups: List<BlockedSiteGroup>,
    val totalBlockedHosts: Int,
    /** `null` in incognito, where the switch is not offered. */
    val fireproof: Boolean?,
) {
    companion object {
        /** `null` when no web page is shown (Home, the incognito start page): no dashboard. */
        fun of(
            status: PageProtectionStatus,
            blockedHosts: List<String>,
            fireproofSites: Set<String>,
            isIncognito: Boolean,
            registrableDomain: (String) -> String,
        ): PageDashboardState? {
            val site = status.site
            if (!status.isWebPage || site == null) return null
            val groups = if (status.protectionOn) {
                BlockedHostGrouper.group(blockedHosts, registrableDomain)
            } else {
                emptyList()
            }
            return PageDashboardState(
                site = site,
                connectionSecure = status.connectionSecure,
                protectionOn = status.protectionOn,
                blockedGroups = groups,
                totalBlockedHosts = groups.sumOf { it.hosts.size },
                fireproof = if (isIncognito) null else IsSiteFireproofUseCase.matches(site, fireproofSites),
            )
        }
    }
}
