package com.raumanian.thirtysix.browser.core.constants

/**
 * Centralized URL constants per Constitution §III No-Hardcode Rule.
 *
 * Spec 007 introduced [DEFAULT_HOME_URL] (initial value `https://example.com`).
 * Spec 008 (`navigation-controls`) bumps the value to `https://www.google.com/`
 * — same constant serves both the initial load on `BrowserScreen` AND the Home
 * affordance on the bottom bar (see `presentation/browser/components/NavigationBottomBar`).
 * Spec 010 will add Google search URL templates; Spec 016 will let the user
 * override [DEFAULT_HOME_URL] from the Settings screen via DataStore.
 */
object UrlConstants {
    /**
     * Default page loaded by `BrowserScreen` on initial composition AND by tapping
     * the Home affordance from any URL. Set to Google homepage in Spec 008 (clarified
     * via `/speckit-clarify` Q2 2026-05-01) — matches the default search engine
     * (Google) configured in Spec 006 and gives first-time users a ready-to-search
     * page rather than an empty `about:blank`.
     */
    const val DEFAULT_HOME_URL: String = "https://www.google.com/"
}
