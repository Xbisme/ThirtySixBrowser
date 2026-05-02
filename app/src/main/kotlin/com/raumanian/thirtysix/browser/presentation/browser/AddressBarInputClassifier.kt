package com.raumanian.thirtysix.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.core.constants.UrlPatterns

private val urlHeuristicRegex = Regex(UrlPatterns.URL_HEURISTIC_REGEX)
private val newlineRegex = Regex(UrlPatterns.ADDRESS_BAR_NEWLINE_REGEX)

/**
 * Spec 009 — classify a raw address-bar input into [AddressBarSubmitResult].
 * Pure function — no I/O, no Android imports. Heuristic per [research.md R2]:
 *
 * 1. Replace any internal newlines with spaces (FR-007).
 * 2. Trim leading/trailing whitespace (FR-006).
 * 3. If the trimmed value is empty, return [AddressBarSubmitResult.Empty]
 *    (FR-012).
 * 4. Else match against [UrlPatterns.URL_HEURISTIC_REGEX] —
 *    if match: build [AddressBarSubmitResult.Url] with a scheme prepended via
 *    [UrlConstants.HTTPS_SCHEME_PREFIX] when needed (FR-009).
 *    else: return [AddressBarSubmitResult.Query] with the trimmed text.
 *
 * Edge cases (documented in `spec.md` Edge Cases section):
 *  - `localhost` (no dot, no scheme) → [AddressBarSubmitResult.Query] —
 *    accepted v1 trade-off.
 *  - `kotlin.coroutines` (looks like package name) → [AddressBarSubmitResult.Url] —
 *    accepted false positive.
 *  - Uppercase scheme `HTTPS://example.com` → [AddressBarSubmitResult.Url]
 *    preserving the original scheme casing.
 */
internal fun classifyAddressBarInput(raw: String): AddressBarSubmitResult {
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
