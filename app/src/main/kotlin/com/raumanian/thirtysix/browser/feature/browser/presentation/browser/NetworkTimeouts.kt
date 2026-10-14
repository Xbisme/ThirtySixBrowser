package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** Time limits for network-adjacent work (Constitution §III). */
object NetworkTimeouts {
    /**
     * Spec 023 research R10 — the longest a background caller waits for the bundled protection
     * lists to finish parsing. Past it the request goes ahead unprotected (fail open) rather than
     * stalling a page.
     */
    const val PROTECTION_DATA_LOAD_TIMEOUT_MS: Long = 3_000L
}
