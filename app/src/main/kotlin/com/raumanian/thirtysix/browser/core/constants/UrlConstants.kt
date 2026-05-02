package com.raumanian.thirtysix.browser.core.constants

/**
 * Centralized URL constants per Constitution §III No-Hardcode Rule.
 *
 * Spec 007 introduced [DEFAULT_HOME_URL] (initial value `https://example.com`).
 * Spec 008 (`navigation-controls`) bumped the value to `https://www.google.com/`.
 * Spec 009 (`address-bar-omnibox`) adds [GOOGLE_SEARCH_URL_TEMPLATE] for inline
 * use by `AddressBarInputClassifier` query-path; Spec 010 will move query
 * handling into a `SearchEngineRepository` and may relocate this constant.
 * Spec 009 also adds [HTTPS_SCHEME_PREFIX] used when the classifier prepends a
 * scheme to a typed URL (FR-009).
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

    /**
     * Google search URL template (Spec 009). `%s` is substituted with the
     * URL-encoded query via `String.format(...)` inside
     * `BrowserViewModel.onAddressBarSubmit` when the input classifies as a
     * query. Spec 010 will introduce a `SearchEngineRepository` abstraction;
     * the template stays here as the Google-specific implementation.
     */
    const val GOOGLE_SEARCH_URL_TEMPLATE: String = "https://www.google.com/search?q=%s"

    /**
     * Scheme prefix prepended by `AddressBarInputClassifier` when the user
     * input matches the URL heuristic but lacks a scheme (FR-009). Constitution
     * §III "URLs (default home, search) → UrlConstants.kt" mapping covers
     * scheme literals as well as full URLs — keeps the classifier free of
     * inline string literals.
     */
    const val HTTPS_SCHEME_PREFIX: String = "https://"
}
