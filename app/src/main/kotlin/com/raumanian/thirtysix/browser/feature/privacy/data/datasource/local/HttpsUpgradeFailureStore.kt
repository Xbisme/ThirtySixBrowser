package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.repository.HttpsUpgradeFailures
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory [HttpsUpgradeFailures] for the life of the process. */
@Singleton
class HttpsUpgradeFailureStore @Inject constructor() : HttpsUpgradeFailures {
    private val hosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun markFailed(host: String) {
        hosts += host.lowercase()
    }

    override fun snapshot(): Set<String> = hosts

    override fun clear() {
        hosts.clear()
    }
}
