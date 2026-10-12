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
     * Spec 019 (research R1, data-model §1) — reserved address of a tab whose content is the
     * Home page (or, in an incognito tab, the incognito page). Persisted in `tabs.url`; never
     * loaded into a WebView, never recorded in history, never shown in the address well.
     */
    const val HOME_PAGE_URL: String = "thirtysix:home"

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

    /**
     * Spec 021 research R5 — the base of the address a page-opened tab is created with (a
     * unique fragment is appended); the pop-up's own navigation replaces it as soon as the
     * adopted WebView reports its address.
     */
    const val POPUP_PLACEHOLDER_URL: String = "about:blank"

    /**
     * Spec 020 research R8 — a web address used only to ask the system, on Android 7–9, which app
     * opens web links by default. Never loaded.
     */
    const val DEFAULT_BROWSER_PROBE_URL: String = "https://example.com"

    /** Spec 023 research R7 — plain-web scheme an address may be upgraded from. */
    const val SCHEME_HTTP: String = "http"

    /** Spec 023 research R7 — secure scheme an address is upgraded to. */
    const val SCHEME_HTTPS: String = "https"

    /** Spec 023 FR-030 — Global Privacy Control request header name. */
    const val GPC_HEADER_NAME: String = "Sec-GPC"

    /** Spec 023 FR-030 — Global Privacy Control header value ("do not sell or share"). */
    const val GPC_HEADER_VALUE: String = "1"

    /** Spec 023 research R4 — HTTP status of the empty response returned for a blocked request. */
    const val BLOCKED_RESPONSE_STATUS: Int = 403

    /** Spec 023 research R4 — reason phrase of a blocked response (the platform rejects an empty one). */
    const val BLOCKED_RESPONSE_REASON: String = "Blocked"

    /** Spec 023 research R4 — MIME type of a blocked response. */
    const val BLOCKED_RESPONSE_MIME: String = "text/plain"

    /** Spec 023 research R4 — encoding of a blocked response. */
    const val BLOCKED_RESPONSE_ENCODING: String = "utf-8"
}
