package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** Sites whose cookies and site data survive Fire. Normal browsing only. */
interface FireproofSiteRepository {
    /** Newest first. */
    fun observeAll(): Flow<List<FireproofSite>>

    /** Synchronous snapshot of the registrable domains; never touches the database. */
    fun currentSites(): Set<String>

    suspend fun add(site: String): Result<Unit>
    suspend fun remove(site: String): Result<Unit>
    suspend fun clearAll(): Result<Unit>
}
