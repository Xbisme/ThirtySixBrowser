package com.raumanian.thirtysix.browser.core.extensions

import androidx.core.net.toUri

/**
 * Spec 009 — URL helper extensions used by the address-bar omnibox.
 *
 * Per Constitution §III, regex / parsing logic for URLs lives in
 * `core/constants/UrlPatterns.kt` (constants) and `core/extensions/`
 * (pure-function helpers). The hostname extractor below uses
 * [androidx.core.net.toUri] (KTX wrapper around `Uri.parse`) which handles
 * every scheme Android understands (http/https, file://, content://, custom).
 * Pure function — no I/O.
 *
 * **`www.` policy** (research R8): we do NOT strip `www.` from the displayed
 * hostname. Stripping is a polish choice that browsers disagree on; preserving
 * the raw host gives copy/paste fidelity, makes UI tests deterministic, and
 * avoids hiding a meaningful subdomain (cookies, SSO).
 */
internal fun String.extractHostnameOrSelf(): String {
    if (isBlank()) return this
    val host = this.toUri().host
    return if (host.isNullOrBlank()) this else host
}
