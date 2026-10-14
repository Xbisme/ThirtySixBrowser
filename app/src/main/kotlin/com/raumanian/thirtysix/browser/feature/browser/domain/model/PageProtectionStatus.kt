package com.raumanian.thirtysix.browser.feature.browser.domain.model

/**
 * What the hub shows about the page in the current tab.
 */
data class PageProtectionStatus(
    /** Distinct tracker hosts blocked on this page. */
    val blockedTrackerCount: Int,
    /** False when the page's site is in the person's "without protection" list. */
    val protectionOn: Boolean,
    val connectionSecure: Boolean,
    /** False on the Home page and the incognito start page: the hub hides the section. */
    val isWebPage: Boolean,
    val site: String? = null,
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
