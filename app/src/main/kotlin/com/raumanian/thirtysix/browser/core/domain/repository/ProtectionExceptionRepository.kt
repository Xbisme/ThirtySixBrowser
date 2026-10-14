package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** Sites the person turned protection off for in normal browsing. */
interface ProtectionExceptionRepository {
    /** Newest first. */
    fun observeAll(): Flow<List<SiteProtectionException>>

    /** Synchronous snapshot for WebView IO threads; never touches the database. */
    fun currentSites(): Set<String>

    suspend fun add(site: String): Result<Unit>

    suspend fun remove(site: String): Result<Unit>

    suspend fun clearAll(): Result<Unit>
}
