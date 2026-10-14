package com.raumanian.thirtysix.browser.core.domain.util

object UrlPatterns {
    // A URL is anything with a scheme or a dot. Deliberately simple: `kotlin.coroutines` counts
    // as a URL and `localhost` as a query. Callers match case-insensitively.
    const val URL_HEURISTIC_REGEX: String = "^[a-zA-Z][a-zA-Z0-9+\\-.]*://.*|.*\\..*"

    const val ADDRESS_BAR_NEWLINE_REGEX: String = "[\\r\\n]+"

    // Only explicit http(s) addresses in shared text, so `file.txt` in a sentence is never a
    // domain. Matched case-insensitively; the caller trims trailing punctuation.
    const val EMBEDDED_WEB_URL_REGEX: String = "https?://[^\\s]+"
}
