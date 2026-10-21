package com.raumanian.thirtysix.browser.core.domain.model

/** The hosts a fireproof site's cookies and data live under. */
object FireproofSites {
    fun originsFor(site: String): List<String> = listOf(site, "www.$site")
}
