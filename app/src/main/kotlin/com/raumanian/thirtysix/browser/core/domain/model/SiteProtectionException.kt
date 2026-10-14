package com.raumanian.thirtysix.browser.core.domain.model

import java.time.Instant

/**
 * A site the person turned protection off for in normal browsing.
 */
data class SiteProtectionException(
    /** The registrable domain, e.g. `shop.example`; covers all its subdomains. */
    val site: String,
    val addedAt: Instant,
)
