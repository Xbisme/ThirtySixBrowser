package com.raumanian.thirtysix.browser.core.domain.model

import java.time.Instant

/**
 * Spec 023 FR-013 — a site the person turned protection off for in normal browsing.
 * Shown in Settings as "Sites without protection".
 *
 * @property site the registrable domain, e.g. `shop.example`; covers all its subdomains.
 */
data class SiteProtectionException(
    val site: String,
    val addedAt: Instant,
)
