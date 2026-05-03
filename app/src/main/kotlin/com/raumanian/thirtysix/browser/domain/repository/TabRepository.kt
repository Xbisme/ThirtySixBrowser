package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import kotlinx.coroutines.flow.Flow

/**
 * Owns the persisted multi-tab state (Spec 011).
 *
 * Per Constitution §IV, this interface lives in `domain/repository/` and uses
 * only Kotlin / project domain types. The implementation lives in
 * `data/repository/TabRepositoryImpl.kt` and depends on Spec 005's `TabDao` +
 * `DispatcherProvider` + the `@Named("default_home_url")` constant for empty-
 * state recovery (FR-019 / R5). NO direct Repository → Repository chain — the
 * cross-feature glue from `BrowserViewModel` lives at the use-case layer
 * (`UpdateActiveTabUrlAndTitleUseCase`) per Constitution §IV.
 *
 * Active-tab pointer is derived from `MAX(last_active_at)` — NO `is_active`
 * column, NO Room schema migration (R1).
 */
interface TabRepository {

    /**
     * Hot Flow of every persisted tab, ordered by `lastActiveAt` DESC then
     * `id` ASC (FR-014). Always emits at least one tab — if the underlying
     * table is empty, a fresh home tab is auto-seeded by the implementation
     * before the first emission (FR-019 / R5).
     */
    fun observeTabs(): Flow<List<Tab>>

    /**
     * Create a new tab pointing to [url]. On success, the new tab's
     * `lastActiveAt` is set to `now()` so it becomes the active tab
     * immediately (callers do NOT need a separate [switchActiveTab] call).
     *
     * Cap-reached failures arrive as `Result.Error(throwable = MaxTabsReachedException, ...)`.
     */
    suspend fun createTab(url: String): Result<Tab>

    /**
     * Make [tabId] the active tab. Writes `lastActiveAt = now()` on the
     * leaving tab BEFORE the new active tab so ordering remains stable (R1).
     * No-op if [tabId] does not exist.
     */
    suspend fun switchActiveTab(tabId: Long)

    /**
     * Persist the live URL + title for [tabId]. Called by `BrowserViewModel`
     * via `UpdateActiveTabUrlAndTitleUseCase` on every `onUrlChanged` +
     * `onLoadFinished` + `onTitleReceived` (FR-022). No-op if [tabId] does
     * not exist.
     */
    suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String)

    /**
     * Durably remove [tabId]. If the post-delete count would drop to 0, a
     * fresh home tab is auto-created in the same coroutine (FR-019 / R5).
     * The new active tab is the most-recently-active surviving tab
     * (US3 #2 / R1).
     */
    suspend fun closeTab(tabId: Long)

    /**
     * Wipe every tab and create exactly one fresh home tab. Atomic at the
     * repository layer (Mutex-guarded — R5/R10).
     */
    suspend fun closeAllTabs()

    /**
     * Snapshot of the current tab count. Used by `CreateTabUseCase`
     * pre-flight checks and by `TabsViewModel` for cap-reached UI states
     * (FR-016).
     */
    suspend fun getTabCount(): Int
}

/**
 * Sentinel exception carried by `Result.Error.throwable` when
 * [TabRepository.createTab] fails because [com.raumanian.thirtysix.browser.core.constants.BrowserLimits.MAX_TABS]
 * is already reached.
 *
 * Consumers detect via `(result as? Result.Error)?.throwable is MaxTabsReachedException`.
 * Reuses Spec 002's existing `Result<T>` (single type parameter) — see
 * Spec 011 plan.md research R12 + analyze remediation C2 for why we use a
 * sentinel exception instead of extending `Result` to `Result<T, E>`.
 */
class MaxTabsReachedException : Exception("Maximum number of tabs reached")
