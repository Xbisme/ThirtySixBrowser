package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Spec 021 — in-memory [SitePermissionRepository] with the same https-only rule. */
class FakeSitePermissionRepository : SitePermissionRepository {
    val rows = MutableStateFlow<List<SitePermissionDecision>>(emptyList())

    override fun observeAll(): Flow<List<SitePermissionDecision>> = rows

    override suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision> =
        rows.value.filter { it.origin == origin }.associate { it.capability to it.decision }

    override suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit> {
        if (!SiteOrigin.isSecure(origin)) return Result.Error(IllegalArgumentException("insecure"))
        rows.update { list ->
            list.filterNot { it.origin == origin && it.capability == capability } +
                SitePermissionDecision(origin, capability, decision, decidedAt = 0L)
        }
        return Result.Success(Unit)
    }

    override suspend fun removeSite(origin: String): Result<Unit> {
        rows.update { list -> list.filterNot { it.origin == origin } }
        return Result.Success(Unit)
    }

    override suspend fun clearAll(): Result<Unit> {
        rows.value = emptyList()
        return Result.Success(Unit)
    }
}
