package com.raumanian.thirtysix.browser.domain.repository

/** Spec 023 FR-020 — hosts whose HTTPS attempt failed in this app process. Never saved. */
interface HttpsUpgradeFailures {
    fun markFailed(host: String)

    fun snapshot(): Set<String>
}
