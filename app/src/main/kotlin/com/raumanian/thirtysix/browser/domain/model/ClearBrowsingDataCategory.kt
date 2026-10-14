package com.raumanian.thirtysix.browser.domain.model

/**
 * Spec 016 FR-025 — the three kinds of browsing data the Settings screen can clear
 * (data-model §4).
 *
 * Declaration order is the order they are cleared in. Cookies come before the cache on purpose:
 * whether the web page cache still needs clearing depends on how the cookie step went
 * (spec A16). Downloads, bookmarks, tabs and settings are deliberately not representable here
 * (FR-031).
 */
enum class ClearBrowsingDataCategory {
    History,
    CookiesAndSiteData,
    CachedImagesAndFiles,

    /**
     * Spec 021 FR-010a — remembered camera / microphone / location decisions and this session's
     * one-time grants. Opt-in: never part of the default selection ([DEFAULT_SELECTION]).
     */
    SitePermissions,

    /**
     * Spec 023 FR-016a — sites the person turned protection off for, saved and incognito.
     * Opt-in like [SitePermissions]: never part of [DEFAULT_SELECTION].
     */
    SitesWithoutProtection,

    /**
     * Spec 024 FR-013 — the fireproof list. Opt-in like [SitePermissions]: never part of
     * [DEFAULT_SELECTION]. Cookies and site data never consult it (INV-10).
     */
    FireproofSites,
    ;

    companion object {
        /** What the clear-data dialog ticks when it opens. */
        val DEFAULT_SELECTION: Set<ClearBrowsingDataCategory> = setOf(History, CookiesAndSiteData, CachedImagesAndFiles)
    }
}
