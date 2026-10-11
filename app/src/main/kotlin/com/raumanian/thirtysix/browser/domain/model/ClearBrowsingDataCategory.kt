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
    ;

    companion object {
        /** What the clear-data dialog ticks when it opens. */
        val DEFAULT_SELECTION: Set<ClearBrowsingDataCategory> = setOf(History, CookiesAndSiteData, CachedImagesAndFiles)
    }
}
