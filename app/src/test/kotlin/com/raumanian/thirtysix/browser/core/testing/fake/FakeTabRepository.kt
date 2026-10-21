package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [TabRepository] with the production contract: an empty store seeds one home tab,
 * tabs are ordered by `lastActiveAt` descending then `id`, [createTab] fails at
 * [BrowserLimits.MAX_TABS], and closing the last tab recreates a home tab.
 *
 * @param homeUrl address of the auto-created home tab.
 * @param initialTabs seed list; when non-empty, no home tab is seeded.
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
