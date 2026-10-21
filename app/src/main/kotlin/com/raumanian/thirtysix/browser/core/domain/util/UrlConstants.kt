package com.raumanian.thirtysix.browser.core.domain.util

object UrlConstants {
    // Reserved address of a tab showing the Home (or incognito) page: persisted in `tabs.url`,
    // but never loaded into a WebView, recorded in history or shown in the address bar.
    const val HOME_PAGE_URL: String = "thirtysix:home"

    // Search templates: `%s` takes the URL-encoded query.
    const val GOOGLE_SEARCH_URL_TEMPLATE: String = "https://www.google.com/search?q=%s"

    const val DUCKDUCKGO_SEARCH_URL_TEMPLATE: String = "https://duckduckgo.com/?q=%s"

    const val BING_SEARCH_URL_TEMPLATE: String = "https://www.bing.com/search?q=%s"

    const val HTTPS_SCHEME_PREFIX: String = "https://"

    // A page-opened tab starts here plus a unique fragment, until the pop-up reports its address.
    const val POPUP_PLACEHOLDER_URL: String = "about:blank"

    // Only used to ask Android 7–9 which app opens web links by default; never loaded.
    const val DEFAULT_BROWSER_PROBE_URL: String = "https://example.com"

    const val SCHEME_HTTP: String = "http"

    const val SCHEME_HTTPS: String = "https"

    // Typed input with any other scheme is searched, so the address bar never runs script.
    val ADDRESS_BAR_SCHEMES: Set<String> = setOf(SCHEME_HTTP, SCHEME_HTTPS, "about")

    // Global Privacy Control: "do not sell or share".
    const val GPC_HEADER_NAME: String = "Sec-GPC"

    const val GPC_HEADER_VALUE: String = "1"

    // The empty response returned for a blocked request.
    const val BLOCKED_RESPONSE_STATUS: Int = 403

    // The platform rejects an empty reason phrase.
    const val BLOCKED_RESPONSE_REASON: String = "Blocked"

    const val BLOCKED_RESPONSE_MIME: String = "text/plain"

    const val BLOCKED_RESPONSE_ENCODING: String = "utf-8"
}
