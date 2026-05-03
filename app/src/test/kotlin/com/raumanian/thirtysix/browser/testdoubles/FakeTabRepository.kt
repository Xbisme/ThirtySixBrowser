package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Spec 011 — hand-rolled in-memory fake of [TabRepository] for unit tests.
 *
 * Mirrors the production contract:
 *  - [observeTabs] auto-seeds an empty list with one home tab on first
 *    subscription (FR-019 / R5).
 *  - Emissions are sorted by `lastActiveAt` DESC then `id` ASC (FR-014 / H1).
 *  - [createTab] enforces [BrowserLimits.MAX_TABS] via [MaxTabsReachedException]
 *    inside `Result.Error` (C2 / FR-016).
 *  - [closeTab] auto-recreates a fresh home tab when count would drop to 0
 *    (US3 #3 / R5).
 *
 * **NEW package** `testdoubles/` — first time this pattern appears in the
 * project. Justified because `TabUseCasesTest` and `TabsViewModelTest` (and
 * later `BrowserViewModelTest` extensions) all need an identical fake;
 * extracting once here avoids triplicating the in-memory state machine.
 *
 * @param homeUrl URL used for the auto-seeded fresh home tab when the
 *                repository is empty (Cold start) or after the last close
 *                (US3 #3). Defaults to `https://www.google.com/` to match
 *                Spec 008 production default.
 * @param initialTabs Optional seed list. When non-empty, the auto-seed-on-
 *                    empty rule is skipped on first subscription (since the
 *                    list is non-empty already). Useful for tests that want
 *                    to start with a specific tab roster.
 */
class FakeTabRepository(
    private val homeUrl: String = "https://www.google.com/",
    initialTabs: List<Tab> = emptyList(),
) : TabRepository {

    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(initialTabs)
    private var nextId: Long = (initialTabs.maxOfOrNull(Tab::id) ?: 0L) + 1

    override fun observeTabs(): Flow<List<Tab>> {
        if (state.value.isEmpty()) {
            seedHomeTab()
        }
        return state.asStateFlow()
        // The state flow already carries the user-side ordering as we
        // mutate it; assertions in tests use `value` snapshots which we
        // pre-sort below.
    }

    override suspend fun createTab(url: String): Result<Tab> {
        if (state.value.size >= BrowserLimits.MAX_TABS) {
            return Result.Error(throwable = MaxTabsReachedException())
        }
        val now = System.currentTimeMillis()
        val newTab = Tab(
            id = nextId++,
            url = url,
            title = "",
            position = state.value.size,
            createdAt = now,
            lastActiveAt = now,
        )
        state.update { it + newTab }
        sortAndEmit()
        return Result.Success(newTab)
    }

    override suspend fun switchActiveTab(tabId: Long) {
        val now = System.currentTimeMillis()
        state.update { tabs ->
            tabs.map { if (it.id == tabId) it.copy(lastActiveAt = now) else it }
        }
        sortAndEmit()
    }

    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
        state.update { tabs ->
            tabs.map { if (it.id == tabId) it.copy(url = url, title = title) else it }
        }
        sortAndEmit()
    }

    override suspend fun closeTab(tabId: Long) {
        state.update { tabs -> tabs.filterNot { it.id == tabId } }
        if (state.value.isEmpty()) seedHomeTab()
        sortAndEmit()
    }

    override suspend fun closeAllTabs() {
        state.value = emptyList()
        seedHomeTab()
        sortAndEmit()
    }

    override suspend fun getTabCount(): Int = state.value.size

    /** Test-only direct seeding helper. */
    fun emit(tabs: List<Tab>) {
        state.value = tabs.sortedWith(
            compareByDescending(Tab::lastActiveAt).thenBy(Tab::id),
        )
    }

    /** Test-only reset helper between scenarios. */
    fun reset() {
        state.value = emptyList()
        nextId = 1L
    }

    private fun seedHomeTab() {
        val now = System.currentTimeMillis()
        state.value = listOf(
            Tab(
                id = nextId++,
                url = homeUrl,
                title = "",
                position = 0,
                createdAt = now,
                lastActiveAt = now,
            ),
        )
    }

    private fun sortAndEmit() {
        state.update { tabs ->
            tabs.sortedWith(compareByDescending(Tab::lastActiveAt).thenBy(Tab::id))
        }
    }
}
