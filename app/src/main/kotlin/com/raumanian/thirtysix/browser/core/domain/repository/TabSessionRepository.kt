package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import kotlinx.coroutines.flow.StateFlow

/**
 * Each tab's saved back and forward. Normal tabs are kept on the device; incognito tabs
 * (negative ids) only in memory.
 */
interface TabSessionRepository {
    /** Increments on every [clearAll], so a live page can drop its back and forward too. */
    val clears: StateFlow<Long>

    /** Null when nothing is saved or the saved state cannot be read. */
    suspend fun get(tabId: Long): TabSession?

    /** A state over the size bound is not kept. */
    suspend fun save(session: TabSession)

    suspend fun delete(tabId: Long)

    suspend fun retainOnly(tabIds: Set<Long>)

    suspend fun clearAll()
}
