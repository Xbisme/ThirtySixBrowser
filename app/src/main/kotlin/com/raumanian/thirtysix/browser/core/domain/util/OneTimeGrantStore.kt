package com.raumanian.thirtysix.browser.core.domain.util

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Allow this time" answers. In memory only: they belong to one tab's current page and end
 * when that page changes, the tab closes or the process ends. Main thread only.
 */
@Singleton
class OneTimeGrantStore @Inject constructor() {
    private data class Key(val tabId: Long, val generation: Long, val origin: String, val capability: SiteCapability)

    private val grants = mutableSetOf<Key>()

    fun grant(tabId: Long, generation: Long, origin: String, capabilities: Set<SiteCapability>) {
        capabilities.forEach { grants += Key(tabId, generation, origin, it) }
    }

    fun has(tabId: Long, generation: Long, origin: String, capability: SiteCapability): Boolean =
        Key(tabId, generation, origin, capability) in grants

    /** The tab's page changed (or the tab closed): its one-time grants end. */
    fun dropForTab(tabId: Long) {
        grants.removeAll { it.tabId == tabId }
    }

    /** Fire keeps only the grants whose origin [keep] accepts (fireproof sites). */
    fun retainOnly(keep: (origin: String) -> Boolean) {
        grants.retainAll { keep(it.origin) }
    }

    fun clear() {
        grants.clear()
    }
}
