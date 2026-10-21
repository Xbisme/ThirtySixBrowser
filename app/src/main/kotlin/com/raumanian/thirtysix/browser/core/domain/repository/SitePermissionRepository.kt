package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** Remembered site decisions, kept on the device only. Only https origins are stored; `save` refuses others. */
interface SitePermissionRepository {
    fun observeAll(): Flow<List<SitePermissionDecision>>

    suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision>

    suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit>

    suspend fun removeSite(origin: String): Result<Unit>

    /** One all-or-nothing delete. */
    suspend fun removeSites(origins: Collection<String>): Result<Unit>

    suspend fun clearAll(): Result<Unit>
}
