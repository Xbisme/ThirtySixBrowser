package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** The active tab is the one with the newest `lastActiveAt`; there is no active-flag column. */
interface TabRepository {

    /** Ordered `lastActiveAt DESC, id ASC`; never empty, as an empty table is seeded with a home tab. */
    fun observeTabs(): Flow<List<Tab>>

    /** The new tab becomes active; the cap fails with [MaxTabsReachedException]. */
    suspend fun createTab(url: String): Result<Tab>

    /** Opens behind the active tab; the default opens an ordinary one so test doubles can skip it. */
    suspend fun createBackgroundTab(url: String, activeAt: Long): Result<Tab> = createTab(url)

    /** Stamps the leaving tab before the new one so the order stays stable. */
    suspend fun switchActiveTab(tabId: Long)

    suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String)

    /** Closing the last tab creates a fresh home tab; the most recently active survivor becomes active. */
    suspend fun closeTab(tabId: Long)

    /** Leaves exactly one fresh home tab. */
    suspend fun closeAllTabs()

    suspend fun getTabCount(): Int
}

/** A sentinel inside `Result.Error`, because `Result` has a single type parameter. */
class MaxTabsReachedException : Exception("Maximum number of tabs reached")
