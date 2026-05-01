package com.raumanian.thirtysix.browser.core.constants

/**
 * Centralized URL constants per Constitution §III No-Hardcode Rule.
 *
 * Spec 007 introduces [DEFAULT_HOME_URL] only — the single page that the v1
 * BrowserScreen loads on launch. Spec 010 will add Google search URL templates;
 * Spec 016 will let the user override [DEFAULT_HOME_URL] from the Settings screen.
 */
object UrlConstants {
    /**
     * Default page loaded by `BrowserScreen` when the app opens. Chosen for v1
     * because the page is stable, lightweight, has well-known content for
     * assertion in `BrowserScreenInstrumentedTest`, and is acceptable for
     * documentation / demos.
     */
    const val DEFAULT_HOME_URL: String = "https://example.com"
}
