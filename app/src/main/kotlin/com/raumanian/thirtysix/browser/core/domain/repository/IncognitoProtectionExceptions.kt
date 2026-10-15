package com.raumanian.thirtysix.browser.core.domain.repository

/**
 * Sites with protection off in incognito tabs. Memory only, cleared when the last incognito tab
 * closes. Incognito never reads the normal list.
 */
interface IncognitoProtectionExceptions {
    fun add(site: String)

    fun remove(site: String)

    fun contains(site: String): Boolean

    fun clear()

    /** For the page-start scripts. */
    fun snapshot(): Set<String>
}
