package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoWebProfile
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.domain.util.TabOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * In-memory incognito tabs, so process death leaves none behind. Ids count down from -1 and are
 * never reused, disjoint from Room's positive ids. Where the engine has profiles the session
 * gets its own (`0 → 1` begins it, `1 → 0` ends it); otherwise the shared cookie jar is
 * snapshotted over [IncognitoSnapshotOrigins] and restored at `1 → 0`.
 */
@Singleton
@Suppress("TooManyFunctions") // One function per contract member.
class IncognitoTabRepositoryImpl @Inject constructor(
    private val cookieJarSnapshotManager: CookieJarSnapshotManager,
    private val snapshotOrigins: IncognitoSnapshotOrigins,
    private val dispatchers: DispatcherProvider,
    private val incognitoProtectionExceptions: IncognitoProtectionExceptions,
    private val incognitoProfile: IncognitoWebProfile,
) : IncognitoTabRepository {

    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    private val nextId: AtomicLong = AtomicLong(0L)
    private val nextPosition: AtomicInteger = AtomicInteger(0)
    private val mutex: Mutex = Mutex()

    /** Whether the running session uses its own profile; decided when it begins. Guarded by [mutex]. */
    private var isolatedSession = false

    override fun observeTabs(): Flow<List<Tab>> = state.asStateFlow()
        .map { list ->
            list.sortedWith(
                compareByDescending(Tab::lastActiveAt).thenBy(Tab::id),
            )
        }

    override suspend fun createTab(url: String): Result<Tab> = insertTab(url, lastActiveAt = null)

    override suspend fun createBackgroundTab(url: String, activeAt: Long): Result<Tab> =
        insertTab(url, lastActiveAt = activeAt - 1)

    /** [lastActiveAt] `null` → now, making the new tab the active one. */
    private suspend fun insertTab(url: String, lastActiveAt: Long?): Result<Tab> =
        withContext(dispatchers.io) {
            mutex.withLock {
                if (state.value.size >= BrowserLimits.MAX_INCOGNITO_TABS) {
                    return@withLock Result.Error(throwable = MaxIncognitoTabsReachedException())
                }
                // Snapshot before adding the tab, so no cookie this tab writes can enter
                // the captured normal-browsing set.
                if (state.value.isEmpty()) beginSession()
                val now = System.currentTimeMillis()
                val newTab = Tab(
                    id = nextId.decrementAndGet(),
                    url = url,
                    title = "",
                    position = nextPosition.getAndIncrement(),
                    createdAt = now,
                    lastActiveAt = lastActiveAt ?: now,
                    isIncognito = true,
                )
                state.value = state.value + newTab
                Result.Success(newTab)
            }
        }

    override suspend fun switchActiveTab(tabId: Long) =
        withContext(dispatchers.io) {
            mutex.withLock {
                val now = System.currentTimeMillis()
                state.value = state.value.map { tab ->
                    if (tab.id == tabId) tab.copy(lastActiveAt = now) else tab
                }
            }
        }

    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) =
        withContext(dispatchers.io) {
            mutex.withLock {
                state.value = state.value.map { tab ->
                    if (tab.id == tabId) tab.copy(url = url, title = title) else tab
                }
            }
        }

    override suspend fun closeTab(tabId: Long) =
        withContext(dispatchers.io) {
            mutex.withLock {
                val before = state.value
                val closed = before.firstOrNull { it.id == tabId }
                state.value = before.filterNot { it.id == tabId }
                if (closed != null && closed.lastActiveAt >= before.maxOf(Tab::lastActiveAt)) {
                    handActiveMark(before, closed)
                }
                val sizeBefore = before.size
                val sizeAfter = state.value.size
                // Restore after removing the tab, so the jar wipe inside the restore also
                // clears the cookies this tab created.
                if (sizeBefore > 0 && sizeAfter == 0) endSession()
            }
        }

    override suspend fun closeAll(): Boolean =
        withContext(dispatchers.io) {
            mutex.withLock {
                if (state.value.isEmpty()) return@withLock true
                state.value = emptyList()
                endSession()
            }
        }

    /** As for normal tabs: the heir inherits the closed tab's stamp. Caller holds [mutex]. */
    private fun handActiveMark(before: List<Tab>, closed: Tab) {
        val heirId = TabOrder.heirOf(before, closed.id)?.id ?: return
        state.value = state.value.map { tab ->
            if (tab.id == heirId) tab.copy(lastActiveAt = maxOf(tab.lastActiveAt, closed.lastActiveAt)) else tab
        }
    }

    /** `0 → 1`: a profile of its own where the engine has profiles, else the jar snapshot. */
    private suspend fun beginSession() {
        // Feature detection loads the WebView provider: done on the main thread.
        isolatedSession = withContext(dispatchers.main) { incognitoProfile.isIsolated() }
        if (isolatedSession) {
            incognitoProfile.beginSession()
        } else {
            cookieJarSnapshotManager.captureSnapshot(snapshotOrigins.collect())
        }
    }

    /** `1 → 0`: the session's cookies and storage go; normal browsing keeps its own. */
    private suspend fun endSession(): Boolean {
        val restored = if (isolatedSession) {
            incognitoProfile.endSession()
            true
        } else {
            cookieJarSnapshotManager.restoreSnapshot()
        }
        isolatedSession = false
        // Incognito protection choices end with the session.
        incognitoProtectionExceptions.clear()
        return restored
    }
}
