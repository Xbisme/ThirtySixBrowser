package com.raumanian.thirtysix.browser.domain.repository

/**
 * Spec 023 FR-014 — sites switched off in incognito tabs. Held in memory only, never saved,
 * cleared when the last incognito tab closes. Incognito never reads the normal list.
 */
interface IncognitoProtectionExceptions {
    fun add(site: String)

    fun remove(site: String)

    fun contains(site: String): Boolean

    fun clear()
}
