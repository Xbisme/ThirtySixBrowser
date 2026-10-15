package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** In-memory incognito tabs; ids are negative so they never collide with Room's tab ids. */
interface IncognitoTabRepository {

    /** Ordered like normal tabs (`lastActiveAt DESC, id ASC`) so the merged list sorts as one. */
    fun observeTabs(): Flow<List<Tab>>

    /** The first incognito tab captures the cookie jar before this returns. */
    suspend fun createTab(url: String): Result<Tab>

    /** Opens behind the active tab; the default opens an ordinary one so test doubles can skip it. */
    suspend fun createBackgroundTab(url: String, activeAt: Long): Result<Tab> = createTab(url)

    suspend fun switchActiveTab(tabId: Long)

    suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String)

    /** Closing the last incognito tab restores the cookie snapshot before this returns. */
    suspend fun closeTab(tabId: Long)

    /** Restores the cookie snapshot exactly once. */
    suspend fun closeAll()

    suspend fun getCount(): Int
}

class MaxIncognitoTabsReachedException :
    Exception("Maximum number of incognito tabs reached")
