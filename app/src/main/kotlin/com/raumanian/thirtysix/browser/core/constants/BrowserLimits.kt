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
}
