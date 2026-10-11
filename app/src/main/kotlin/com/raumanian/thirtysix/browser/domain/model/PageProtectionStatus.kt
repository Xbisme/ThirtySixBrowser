package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 023 FR-010, FR-011, FR-022a — what the hub shows about the page in the current tab.
 *
 * @property blockedTrackerCount distinct tracker hosts blocked on this page.
 * @property protectionOn false when the page's site is in the person's "without protection" list.
 * @property connectionSecure false when the page was loaded over `http://`.
 * @property isWebPage false on the Home page and the incognito start page: the hub hides the section.
 */
data class PageProtectionStatus(
    val blockedTrackerCount: Int,
    val protectionOn: Boolean,
    val connectionSecure: Boolean,
    val isWebPage: Boolean,
) {
    companion object {
        val NOT_A_WEB_PAGE: PageProtectionStatus = PageProtectionStatus(
            blockedTrackerCount = 0,
            protectionOn = true,
            connectionSecure = true,
            isWebPage = false,
        )
    }
}
