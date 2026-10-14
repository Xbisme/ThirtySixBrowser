package com.raumanian.thirtysix.browser.feature.settings.domain.model

/**
 * Declaration order is clearing order: cookies come before the cache because a complete cookie
 * clear also empties the web cache. Tabs, bookmarks, downloads and settings are deliberately absent.
 */
enum class ClearBrowsingDataCategory {
    History,
    CookiesAndSiteData,
    CachedImagesAndFiles,

    // The three below are opt-in: never part of DEFAULT_SELECTION.
    SitePermissions,

    SitesWithoutProtection,

    // Cookies and site data never consult the fireproof list.
    FireproofSites,
    ;

    companion object {
        val DEFAULT_SELECTION: Set<ClearBrowsingDataCategory> = setOf(History, CookiesAndSiteData, CachedImagesAndFiles)
    }
}
