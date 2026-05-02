package com.raumanian.thirtysix.browser.core.constants

/**
 * Regex constants for URL detection and address-bar input normalization
 * (Spec 009). Per Constitution §III mapping table, regex patterns belong
 * in `core/constants/UrlPatterns.kt`.
 *
 * The URL heuristic is intentionally simple per [research.md R2]:
 * - matches when the input contains a scheme followed by `://` near the start, OR
 * - contains at least one `.` (dot) anywhere in the trimmed value.
 *
 * Edge cases like `localhost` (no dot → classified as query) are accepted
 * as v1 trade-offs documented in `spec.md` edge-case section.
 */
object UrlPatterns {
    /**
     * URL classification heuristic used by `AddressBarInputClassifier` (Spec 009).
     * Two alternation branches:
     *  1. `^[a-zA-Z][a-zA-Z0-9+\-.]*://.*` — scheme-prefixed URL (RFC 3986 scheme
     *     character class; case-insensitive matching is the consumer's
     *     responsibility via `Regex(...,RegexOption.IGNORE_CASE)`).
     *  2. `.*\..*` — any string containing a literal dot.
     *
     * The simple second branch produces deliberate false-positives like
     * `kotlin.coroutines` → URL. Spec edge-case section documents this as
     * acceptable for v1.
     */
    const val URL_HEURISTIC_REGEX: String = "^[a-zA-Z][a-zA-Z0-9+\\-.]*://.*|.*\\..*"

    /**
     * Matches one or more newline characters (CR or LF). Used by the address-bar
     * classifier to strip newlines from pasted multi-line input before
     * trimming and classifying (FR-007).
     */
    const val ADDRESS_BAR_NEWLINE_REGEX: String = "[\\r\\n]+"
}
