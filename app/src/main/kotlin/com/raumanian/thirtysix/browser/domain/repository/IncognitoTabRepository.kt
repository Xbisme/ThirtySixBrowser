package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import kotlinx.coroutines.flow.Flow

/**
 * Spec 012 — owns the in-memory incognito-tab state.
 *
 * Parallel to [TabRepository] (Room-backed normal tabs). The two repositories
 * are unioned at the use-case layer (`ObserveAllTabsUseCase`) so the switcher
 * sees one ordered list. They never depend on each other (Constitution §IV
 * forbids `Repository → Repository`); cross-repo coordination lives in the
 * use-case layer (`CloseIncognitoTabUseCase` is the documented §IV exception
 * because it must coordinate `IncognitoTabRepository` + `CookieJarSnapshotManager`
 * to honour FR-011a).
 *
 * Concurrency: all mutating methods serialize on an internal mutex inside the
 * implementation. [observeTabs] returns a [kotlinx.coroutines.flow.StateFlow]
 * view of the in-memory state.
 *
 * Process death contract (FR-012): the implementation is `@Singleton`; the
 * application process owns the only reference to the in-memory state. Process
 * termination wipes the state. Cold start re-creates the singleton with
 * `state = emptyList()`. NO defensive read of any persisted source.
 *
 * ID-space invariant (R3): every Tab emitted by this repository has a
 * NEGATIVE Long id (≤ -1). This guarantees disjoint key space with
 * [TabRepository]'s positive auto-increment Room PKs in the merged Flow.
 */
interface IncognitoTabRepository {

    /**
     * Hot Flow of every currently-open incognito tab. Sorted by
     * `lastActiveAt DESC, id ASC` (matches the Spec 011 normal-tab ordering
     * rule so the merged Flow has a single coherent sort).
     *
     * Emits an empty list at app launch and after the last incognito tab
     * is closed.
     */
    fun observeTabs(): Flow<List<Tab>>

    /**
     * Open a new incognito tab pointing to [url]. The new tab is activated
     * immediately (its `lastActiveAt = now()`).
     *
     * Side effects (atomic with the tab creation, mutex-guarded):
     *  1. If this is the FIRST incognito tab in the current session
     *     (transition `0 → 1`), the global cookie jar is captured into a
     *     snapshot via [CookieJarSnapshotManager.captureSnapshot] BEFORE
     *     this method returns.
     *  2. The new [Tab] is appended to the in-memory state with
     *     `isIncognito = true` and a NEGATIVE Long id.
     *
     * Cap-reached failures arrive as
     * `Result.Error(throwable = MaxIncognitoTabsReachedException, ...)` so
     * callers can disambiguate from the normal-tab cap (per FR-004).
     */
    suspend fun createTab(url: String): Result<Tab>

    /**
     * Make [tabId] the active incognito tab. Writes `lastActiveAt = now()`
     * on the matching entry. No-op if [tabId] does not exist.
     */
    suspend fun switchActiveTab(tabId: Long)

    /**
     * Update the live URL + title of an incognito tab in the in-memory state.
     * Called by `UpdateActiveTabUrlAndTitleUseCase` when the active tab is
     * incognito (instead of writing to [TabRepository]). No-op if [tabId]
     * does not exist.
     */
    suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String)

    /**
     * Close [tabId]. Mutex-guarded.
     *
     * Side effects:
     *  1. Tab is removed from the in-memory state.
     *  2. If the post-close incognito count would drop to 0
     *     (transition `1 → 0`), the cookie jar snapshot is restored via
     *     [CookieJarSnapshotManager.restoreSnapshot] BEFORE this method
     *     returns.
     *
     * No-op if [tabId] does not exist.
     */
    suspend fun closeTab(tabId: Long)

    /**
     * Close every incognito tab. Mutex-guarded; atomic.
     *
     * Side effects:
     *  1. Wipe the in-memory state to empty.
     *  2. Restore the cookie jar snapshot exactly once.
     */
    suspend fun closeAll()

    /**
     * Snapshot of the current incognito tab count. Used by
     * `CreateIncognitoTabUseCase` pre-flight checks (FR-004) and by
     * `TabsViewModel` for affordance visibility logic (US5 #1/#2).
     */
    suspend fun getCount(): Int
}

/**
 * Sentinel exception carried by [Result.Error.throwable] when
 * [IncognitoTabRepository.createTab] fails because
 * [com.raumanian.thirtysix.browser.core.constants.BrowserLimits.MAX_INCOGNITO_TABS]
 * is already reached.
 *
 * Mirrors [MaxTabsReachedException] from Spec 011 — same handling pattern,
 * different cap.
 */
class MaxIncognitoTabsReachedException :
    Exception("Maximum number of incognito tabs reached")
