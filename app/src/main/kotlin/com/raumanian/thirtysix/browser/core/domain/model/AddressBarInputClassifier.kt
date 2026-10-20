package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.domain.util.UrlPatterns
private val urlHeuristicRegex = Regex(UrlPatterns.URL_HEURISTIC_REGEX)
private val newlineRegex = Regex(UrlPatterns.ADDRESS_BAR_NEWLINE_REGEX)

/**
 * Classifies address-bar input: newlines become spaces, blank input is [AddressBarSubmitResult.Empty],
 * an `http`, `https` or `about` address stays as typed, dotted input without spaces becomes a URL
 * with `https://` added, and the rest is a search query.
 */
fun classifyAddressBarInput(raw: String): AddressBarSubmitResult {
    val cleaned = raw.replace(newlineRegex, " ").trim()
    if (cleaned.isEmpty()) return AddressBarSubmitResult.Empty
    val target = when {
        SCHEME_SEPARATOR in cleaned ->
            cleaned.takeIf { it.substringBefore(SCHEME_SEPARATOR).lowercase() in UrlConstants.ADDRESS_BAR_SCHEMES }
        cleaned.none(Char::isWhitespace) && urlHeuristicRegex.matches(cleaned) ->
            "${UrlConstants.HTTPS_SCHEME_PREFIX}$cleaned"
        else -> null
    }
    return target?.let(AddressBarSubmitResult::Url) ?: AddressBarSubmitResult.Query(cleaned)
}

private const val SCHEME_SEPARATOR = "://"
