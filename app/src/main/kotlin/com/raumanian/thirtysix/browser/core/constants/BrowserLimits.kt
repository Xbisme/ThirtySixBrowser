package com.raumanian.thirtysix.browser.core.constants

/**
 * Spec 011 — limits for the multi-tab system.
 *
 * Reserved per Constitution §III table row "Magic numbers / limits → core/constants/BrowserLimits.kt".
 * Future limits (max history days, max bookmarks, etc.) live here as the corresponding specs
 * (014 history, 013 bookmarks) ship.
 */
object BrowserLimits {
    /**
     * Maximum number of open tabs the user may keep at once. When reached, the new-tab
     * affordances (BrowserScreen long-press AND TabsScreen "new tab" card) are visually
     * disabled and a localized "max tabs reached" message is surfaced (FR-016 / SC-004).
     *
     * Value 50 is mid-range for Android browsers (Chrome ~100, DuckDuckGo no hard cap,
     * Firefox no hard cap); 50 keeps memory predictable on min-spec devices (Android 7,
     * 2 GB RAM) given Spec 011's single-active-WebView strategy (FR-027 / R8).
     */
    const val MAX_TABS: Int = 50

    /**
     * Spec 012 — independent ceiling for incognito tabs (Q1 clarification).
     *
     * Incognito tabs do NOT count against [MAX_TABS]. A user with [MAX_TABS] normal
     * tabs can still open up to [MAX_INCOGNITO_TABS] incognito tabs. Defaults to the
     * same value as [MAX_TABS] for symmetry; can be tuned independently if memory
     * pressure surfaces in stress tests (SC-005, the 100-cycle gate).
     *
     * Hitting this cap surfaces a distinct localized error string
     * (`tabs_error_max_incognito_tabs_reached`) — different from the [MAX_TABS]
     * cap message — so users immediately understand which kind of tab is full.
     */
    const val MAX_INCOGNITO_TABS: Int = 50
}
