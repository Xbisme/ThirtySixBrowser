package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Spec 024 FR-009 – FR-013 — the sites whose cookies and site data survive Fire. Normal browsing
 * only: incognito never fireproofs a site (FR-011).
 */
interface FireproofSiteRepository {
    /** Newest first. */
    fun observeAll(): Flow<List<FireproofSite>>

    /** Synchronous snapshot of the registrable domains; never touches the database. */
    fun currentSites(): Set<String>

    suspend fun add(site: String): Result<Unit>
    suspend fun remove(site: String): Result<Unit>
    suspend fun clearAll(): Result<Unit>
}
