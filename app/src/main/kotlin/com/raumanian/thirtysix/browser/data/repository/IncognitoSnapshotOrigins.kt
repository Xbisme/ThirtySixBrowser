package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Spec 012 snapshot path (engines without WebView profiles) — the origins whose cookies are set
 * aside when an incognito session begins and written back when it ends. Cookies of any other site
 * are lost at the end of the session there, so the set is as wide as the app knows: every normal
 * tab, plus `https` and `http` for every host in history and every fireproof site (and its
 * `www.`), so a site visited earlier or kept on purpose stays signed in.
 *
 * Reads three repositories at the moment the session begins (the documented Spec 012 exception
 * to "repositories never depend on repositories", kept in this one class). Every read is defensive:
 * a failed read keeps what the others returned, and malformed URLs are dropped.
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
            runCatching { fireproofSites.currentSites().flatMap { listOf(it, "$WWW$it") } }.getOrElse { emptyList() }
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
