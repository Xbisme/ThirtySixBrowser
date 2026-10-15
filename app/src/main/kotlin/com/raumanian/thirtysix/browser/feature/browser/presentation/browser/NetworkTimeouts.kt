package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** Time limits for network-adjacent work. */
object NetworkTimeouts {
    /** Past this a request goes ahead unprotected (fails open) rather than stalling the page while the lists parse. */
    const val PROTECTION_DATA_LOAD_TIMEOUT_MS: Long = 3_000L
}
