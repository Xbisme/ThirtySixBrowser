package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 *  - Transition `0 → 1` (first incognito tab): [createTab] calls
 *    [CookieJarSnapshotManager.captureSnapshot] with origins enumerated
 *    from currently-persisted normal tabs (via [tabRepository.observeTabs]).
 *  - Transition `1 → 0` (last incognito tab closed): [closeTab] / [closeAll]
 *    call [CookieJarSnapshotManager.restoreSnapshot].
 *
 * Constitution §IV: this repository depends on [CookieJarSnapshotManager]
 * (a CoreService-style platform-state manager, NOT a feature repository) and
 * on [TabRepository] (read-only, only for origin enumeration at snapshot
 * capture time). The latter is a Repository → Repository read; per
 * [plan.md Complexity Tracking](../../../../../../specs/012-private-incognito-mode/plan.md),
 * the cross-feature glue normally lives at the use-case layer, BUT this
 * specific read is gated by the snapshot-capture protocol which is
 * inseparable from the createTab transaction. Documented exception, same
 * pattern as Spec 011 `UpdateActiveTabUrlAndTitleUseCase`.
 */
@Singleton
class IncognitoTabRepositoryImpl @Inject constructor(
    private val cookieJarSnapshotManager: CookieJarSnapshotManager,
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
) : IncognitoTabRepository {

    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    private val nextId: AtomicLong = AtomicLong(0L)
    private val mutex: Mutex = Mutex()

    override fun observeTabs(): Flow<List<Tab>> = state.asStateFlow()
        .map { list ->
            list.sortedWith(
                compareByDescending(Tab::lastActiveAt).thenBy(Tab::id),
            )
        }

    override suspend fun createTab(url: String): Result<Tab> =
        withContext(dispatchers.io) {
            mutex.withLock {
                if (state.value.size >= BrowserLimits.MAX_INCOGNITO_TABS) {
                    return@withLock Result.Error(throwable = MaxIncognitoTabsReachedException())
                }
                // 0 → 1 transition: capture snapshot BEFORE adding tab so any
                // cookie write triggered by this incognito tab cannot bias the
                // captured normal-tab cookie set.
                if (state.value.isEmpty()) {
                    val origins = enumerateNormalTabOrigins()
                    cookieJarSnapshotManager.captureSnapshot(origins)
                }
                val now = System.currentTimeMillis()
                val newTab = Tab(
                    id = nextId.decrementAndGet(),
                    url = url,
                    title = "",
                    position = state.value.size,
                    createdAt = now,
                    lastActiveAt = now,
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
                if (sizeBefore > 0 && sizeAfter == 0) {
                    cookieJarSnapshotManager.restoreSnapshot()
                }
            }
        }

    override suspend fun closeAll() =
        withContext(dispatchers.io) {
            mutex.withLock {
                if (state.value.isEmpty()) return@withLock
                state.value = emptyList()
                cookieJarSnapshotManager.restoreSnapshot()
            }
        }

    override suspend fun getCount(): Int = state.value.size

    /**
     * Enumerate the unique origins (scheme + host + port) of all currently-
     * persisted normal tabs. Used for the snapshot-capture origin list.
     *
     * Bounded by [BrowserLimits.MAX_TABS]; typical N ≤ 10 in practice.
     * Defensive `runCatching` — malformed URLs are silently dropped.
     */
    private suspend fun enumerateNormalTabOrigins(): List<String> = runCatching {
        tabRepository.observeTabs().first()
            .mapNotNull { tab -> originOf(tab.url) }
            .distinct()
    }.getOrElse { emptyList() }

    private fun originOf(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = uri.port
        if (port == -1) "$scheme://$host" else "$scheme://$host:$port"
    }.getOrNull()
}
