package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.domain.model.SitePermissionDecision
import kotlinx.coroutines.flow.Flow

/**
 * Spec 021 contracts C3.2 — remembered site decisions, kept on the device only (FR-010).
 * Only https origins are ever stored (INV-2); a `save` for another origin is refused.
 */
interface SitePermissionRepository {
    fun observeAll(): Flow<List<SitePermissionDecision>>

    suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision>

    suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit>

    suspend fun removeSite(origin: String): Result<Unit>

    suspend fun clearAll(): Result<Unit>
}
