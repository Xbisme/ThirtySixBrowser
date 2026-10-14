package com.raumanian.thirtysix.browser.core.domain.model

import java.time.Instant

/**
 * Spec 024 FR-009 — a site the person fireproofed in normal browsing. Its cookies and site data
 * survive Fire (FR-010). Shown in Settings as "Fireproof sites".
 *
 * @property site the registrable domain, e.g. `example.com`; covers all its subdomains.
 */
data class FireproofSite(
    val site: String,
    val addedAt: Instant,
)
