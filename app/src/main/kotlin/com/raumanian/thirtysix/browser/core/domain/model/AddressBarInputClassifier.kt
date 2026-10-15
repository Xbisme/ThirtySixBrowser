package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.domain.util.UrlPatterns
private val urlHeuristicRegex = Regex(UrlPatterns.URL_HEURISTIC_REGEX)
private val newlineRegex = Regex(UrlPatterns.ADDRESS_BAR_NEWLINE_REGEX)

/**
 * Classifies address-bar input: newlines become spaces, blank input is [AddressBarSubmitResult.Empty],
 * anything matching the URL heuristic becomes a URL (`https://` added when no scheme was typed),
 * and the rest is a search query.
 */
fun classifyAddressBarInput(raw: String): AddressBarSubmitResult {
    val cleaned = raw.replace(newlineRegex, " ").trim()
    if (cleaned.isEmpty()) return AddressBarSubmitResult.Empty
    return if (urlHeuristicRegex.matches(cleaned)) {
        val target = if (cleaned.contains("://")) {
            cleaned
        } else {
            "${UrlConstants.HTTPS_SCHEME_PREFIX}$cleaned"
        }
        AddressBarSubmitResult.Url(target)
    } else {
        AddressBarSubmitResult.Query(cleaned)
    }
}
