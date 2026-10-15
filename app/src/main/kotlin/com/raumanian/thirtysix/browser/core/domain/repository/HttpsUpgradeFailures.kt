package com.raumanian.thirtysix.browser.core.domain.repository

/** Hosts whose HTTPS attempt failed in this process. Never saved. */
interface HttpsUpgradeFailures {
    fun markFailed(host: String)

    fun snapshot(): Set<String>

    /** Fire forgets every failed host, so the next visit tries HTTPS again. */
    fun clear()
}
