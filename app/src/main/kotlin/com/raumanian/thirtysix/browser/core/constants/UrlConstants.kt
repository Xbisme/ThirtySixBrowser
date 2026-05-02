package com.raumanian.thirtysix.browser.core.constants

/**
 * Centralized URL constants per Constitution §III No-Hardcode Rule.
 *
 * Spec 007 introduced [DEFAULT_HOME_URL] (initial value `https://example.com`).
 * Spec 008 (`navigation-controls`) bumped the value to `https://www.google.com/`.
 * Spec 009 (`address-bar-omnibox`) added [GOOGLE_SEARCH_URL_TEMPLATE] for inline
 * use by `AddressBarInputClassifier` query-path and [HTTPS_SCHEME_PREFIX] used
 * when the classifier prepends a scheme to a typed URL (FR-009).
 * Spec 010 (`search-engine-google`) adds [DUCKDUCKGO_SEARCH_URL_TEMPLATE] +
 * [BING_SEARCH_URL_TEMPLATE] alongside Google; per-call template selection is
 * owned by `SearchEngineRepositoryImpl` reading the user's persisted
 * `SearchEngine` from `SettingsRepository`.
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
     * Google search URL template. `%s` is substituted with the URL-encoded
     * query inside `SearchEngineRepositoryImpl.buildSearchUrl` when the user's
     * persisted `SearchEngine` is `Google`. Originally introduced in Spec 009
     * for inline use by `BrowserViewModel.onAddressBarSubmit`; Spec 010 moved
     * the call site into the `SearchEngineRepository` abstraction.
     */
    const val GOOGLE_SEARCH_URL_TEMPLATE: String = "https://www.google.com/search?q=%s"

    /**
     * DuckDuckGo search URL template (Spec 010, locked via 2026-05-03
     * clarification Q1). Canonical mobile-friendly public endpoint with a
     * single `%s` substitution slot; no API key, cookie, or region pinning
     * required. Substituted inside `SearchEngineRepositoryImpl.buildSearchUrl`
     * when the user's persisted `SearchEngine` is `DuckDuckGo`.
     */
    const val DUCKDUCKGO_SEARCH_URL_TEMPLATE: String = "https://duckduckgo.com/?q=%s"

    /**
     * Bing search URL template (Spec 010, locked via 2026-05-03 clarification
     * Q1). Canonical public endpoint with a single `%s` substitution slot.
     * Substituted inside `SearchEngineRepositoryImpl.buildSearchUrl` when the
     * user's persisted `SearchEngine` is `Bing`.
     */
    const val BING_SEARCH_URL_TEMPLATE: String = "https://www.bing.com/search?q=%s"

    /**
     * Scheme prefix prepended by `AddressBarInputClassifier` when the user
     * input matches the URL heuristic but lacks a scheme (FR-009). Constitution
     * §III "URLs (default home, search) → UrlConstants.kt" mapping covers
     * scheme literals as well as full URLs — keeps the classifier free of
     * inline string literals.
     */
    const val HTTPS_SCHEME_PREFIX: String = "https://"
}
