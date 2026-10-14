package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.webview.IncognitoWebProfile
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.domain.repository.MaxIncognitoTabsReachedException
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
 * Spec 012 — concrete [IncognitoTabRepository].
 *
 * In-memory state ([state]) sourced from a [MutableStateFlow], guarded by
 * [mutex] for all mutations. Process death = singleton dies = state reset to
 * empty (FR-012 satisfied by language semantics; no defensive disk read).
 *
 * ID-space: [nextId] decrements an [AtomicLong] starting at -1L; every
 * incognito tab gets a strictly negative Long id. Disjoint from Room's
 * positive auto-increment PKs. IDs are never reused (counter never resets
 * within a process lifetime).
 *
 * Cookie session lifecycle (FR-011a):
 *  - Where the engine has WebView profiles ([IncognitoWebProfile.isIsolated]), the session gets a
 *    profile of its own: `0 → 1` begins it, `1 → 0` ends it and its profile is deleted. Normal
 *    browsing's cookies and storage are never touched.
 *  - Otherwise (older engines) the shared jar is snapshotted: `0 → 1` calls
 *    [CookieJarSnapshotManager.captureSnapshot] with [IncognitoSnapshotOrigins] (normal tabs,
 *    history, fireproof sites), and `1 → 0` calls [CookieJarSnapshotManager.restoreSnapshot].
 *    Cookies of any other site are still lost at the end of the session there.
 *
 * Constitution §IV: this repository depends on [CookieJarSnapshotManager] and
 * [IncognitoWebProfile] (platform-state managers, NOT feature repositories). The
 * repository reads needed for the snapshot origins live in [IncognitoSnapshotOrigins]
 * (the documented Spec 012 exception, per
 * [plan.md Complexity Tracking](../../../../../../specs/012-private-incognito-mode/plan.md)).
 */
@Singleton
@Suppress("TooManyFunctions") // Spec 021 R9 adds createBackgroundTab; one function per contract member.
class IncognitoTabRepositoryImpl @Inject constructor(
    private val cookieJarSnapshotManager: CookieJarSnapshotManager,
    private val snapshotOrigins: IncognitoSnapshotOrigins,
    private val dispatchers: DispatcherProvider,
    private val incognitoProtectionExceptions: IncognitoProtectionExceptions,
    private val incognitoProfile: IncognitoWebProfile,
) : IncognitoTabRepository {

    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    private val nextId: AtomicLong = AtomicLong(0L)
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
                // 0 → 1 transition: capture snapshot BEFORE adding tab so any
                // cookie write triggered by this incognito tab cannot bias the
                // captured normal-tab cookie set.
                if (state.value.isEmpty()) beginSession()
                val now = System.currentTimeMillis()
                val newTab = Tab(
                    id = nextId.decrementAndGet(),
                    url = url,
                    title = "",
                    position = state.value.size,
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
                val sizeBefore = state.value.size
                state.value = state.value.filterNot { it.id == tabId }
                val sizeAfter = state.value.size
                // 1 → 0 transition: restore snapshot AFTER removing the tab so
                // the live cookie jar wipe inside restoreSnapshot also clears
                // any incognito-set cookies that this tab created.
                if (sizeBefore > 0 && sizeAfter == 0) endSession()
            }
        }

    override suspend fun closeAll() =
        withContext(dispatchers.io) {
            mutex.withLock {
                if (state.value.isEmpty()) return@withLock
                state.value = emptyList()
                endSession()
            }
        }

    override suspend fun getCount(): Int = state.value.size

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
    private suspend fun endSession() {
        if (isolatedSession) incognitoProfile.endSession() else cookieJarSnapshotManager.restoreSnapshot()
        isolatedSession = false
        // Spec 023 FR-014: incognito protection choices end with the session.
        incognitoProtectionExceptions.clear()
    }
}
