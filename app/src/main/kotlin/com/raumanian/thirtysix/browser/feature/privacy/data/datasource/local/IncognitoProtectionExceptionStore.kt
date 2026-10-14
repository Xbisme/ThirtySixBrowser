package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Spec 023 FR-014 — in-memory [IncognitoProtectionExceptions]; nothing here is ever persisted. */
@Singleton
class IncognitoProtectionExceptionStore @Inject constructor() : IncognitoProtectionExceptions {
    private val sites: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun add(site: String) {
        sites += site
    }

    override fun remove(site: String) {
        sites -= site
    }

    override fun contains(site: String): Boolean = site in sites

    override fun clear() {
        sites.clear()
    }

    override fun snapshot(): Set<String> = sites.toSet()
}
