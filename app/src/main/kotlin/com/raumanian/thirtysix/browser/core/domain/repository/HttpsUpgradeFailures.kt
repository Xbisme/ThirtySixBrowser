package com.raumanian.thirtysix.browser.core.domain.repository

/** Spec 023 FR-020 — hosts whose HTTPS attempt failed in this app process. Never saved. */
interface HttpsUpgradeFailures {
    fun markFailed(host: String)

    fun snapshot(): Set<String>

    /** Spec 024 FR-003a — Fire forgets every failed host, so the next visit tries HTTPS again. */
    fun clear()
}
