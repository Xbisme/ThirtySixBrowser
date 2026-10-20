package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * On engines without WebView profiles, the origins whose cookies are set aside when an incognito
 * session begins and written back when it ends. Any other site's cookies are lost, so the set
 * spans normal tabs, history hosts and fireproof sites; a failed read keeps the others' results.
 */
class IncognitoSnapshotOrigins @Inject constructor(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val fireproofSites: FireproofSiteRepository,
) {
    suspend fun collect(): List<String> {
        val tabOrigins = runCatching {
            tabRepository.observeTabs().first().mapNotNull { tab -> originOf(tab.url) }
        }.getOrElse { emptyList() }
        val hosts = runCatching { historyRepository.distinctHosts() }.getOrElse { emptySet() } +
            runCatching {
                fireproofSites.observeAll().first().flatMap { listOf(it.site, "$WWW${it.site}") }
            }.getOrElse { emptyList() }
        return (tabOrigins + hosts.flatMap { host -> listOf("$HTTPS$host", "$HTTP$host") }).distinct()
    }

    private fun originOf(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = uri.port
        if (port == -1) "$scheme://$host" else "$scheme://$host:$port"
    }.getOrNull()

    private companion object {
        const val HTTPS = "https://"
        const val HTTP = "http://"
        const val WWW = "www."
    }
}
