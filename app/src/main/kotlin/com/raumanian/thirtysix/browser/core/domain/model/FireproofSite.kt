package com.raumanian.thirtysix.browser.core.domain.model

import java.time.Instant

/**
 * A site whose cookies and site data survive Fire.
 */
data class FireproofSite(
    /** The registrable domain, e.g. `example.com`; covers all its subdomains. */
    val site: String,
    val addedAt: Instant,
)
