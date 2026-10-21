package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.TabSessionDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabSessionEntity
import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.domain.repository.TabSessionRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Incognito sessions stay in [incognito] and never reach the database, so it must stay a singleton. */
class TabSessionRepositoryImpl @Inject constructor(
    private val dao: TabSessionDao,
    private val dispatchers: DispatcherProvider,
) : TabSessionRepository {

    private val incognito = ConcurrentHashMap<Long, TabSession>()

    // A WebView can save after its incognito tab closed; ids are never reused, so a closed one stays refused.
    private val closedIncognito: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var lastOpenIncognito: Set<Long> = emptySet()

    private val clearCount = MutableStateFlow(0L)

    override val clears: StateFlow<Long> = clearCount.asStateFlow()

    override suspend fun get(tabId: Long): TabSession? {
        if (tabId < 0L) return incognito[tabId]
        return withContext(dispatchers.io) {
            readOrNull { dao.get(tabId) }?.let { TabSession(it.tabId, it.state, it.savedAt) }
        }
    }

    override suspend fun save(session: TabSession) {
        if (session.state.size > BrowserLimits.TAB_STATE_MAX_BYTES) return
        if (session.tabId < 0L) {
            if (session.tabId !in closedIncognito) incognito[session.tabId] = session
            return
        }
        // The tab may have closed meanwhile (a pop-up closing itself): the foreign key then refuses
        // the row, and a lost save of a gone tab is right.
        withContext(dispatchers.io) {
            readOrNull { dao.upsert(TabSessionEntity(session.tabId, session.state, session.savedAt)) }
        }
    }

    override suspend fun retainOnly(tabIds: Set<Long>) {
        val normalIds = tabIds.filter { it > 0L }
        if (normalIds.isEmpty()) return
        val open = tabIds.filterTo(HashSet()) { it < 0L }
        // A new incognito session: the last one's late saves have long landed, so its refusals can go.
        if (lastOpenIncognito.isEmpty() && open.isNotEmpty()) closedIncognito.clear()
        closedIncognito += lastOpenIncognito - open
        lastOpenIncognito = open
        incognito.keys.retainAll(tabIds)
        withContext(dispatchers.io) { readOrNull { dao.deleteNotIn(normalIds) } }
    }

    override suspend fun clearAll() {
        incognito.clear()
        withContext(dispatchers.io) { dao.deleteAll() }
        clearCount.update { it + 1 }
    }

    // A failed read is a missing session (the tab loads its address); a failed write loses one save.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun <T> readOrNull(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
