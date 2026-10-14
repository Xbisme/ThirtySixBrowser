package com.raumanian.thirtysix.browser.presentation.browser.privacy

import com.raumanian.thirtysix.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.domain.privacy.BlockedHostGrouper
import com.raumanian.thirtysix.browser.domain.privacy.BlockedSiteGroup
import com.raumanian.thirtysix.browser.domain.usecase.IsSiteFireproofUseCase

/**
 * Spec 024 data-model "PageDashboardState" — what the privacy dashboard shows for the page on
 * screen (FR-016). There is no "cookie banner declined" line: nothing comes back from the page
 * (research R8).
 *
 * @property fireproof `null` in incognito, where the switch is not offered (FR-011).
 */
data class PageDashboardState(
    val site: String,
    val connectionSecure: Boolean,
    val protectionOn: Boolean,
    val blockedGroups: List<BlockedSiteGroup>,
    val totalBlockedHosts: Int,
    val fireproof: Boolean?,
) {
    companion object {
        /** `null` when no web page is shown (Home, the incognito start page): no dashboard (FR-015). */
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
