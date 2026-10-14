package com.raumanian.thirtysix.browser.feature.sitepermission.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.SitePermissionDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SitePermissionEntity
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.sitepermission.data.mapper.toDomainOrNull
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SitePermissionRepositoryImpl @Inject constructor(
    private val dao: SitePermissionDao,
    private val dispatchers: DispatcherProvider,
) : SitePermissionRepository {

    override fun observeAll(): Flow<List<SitePermissionDecision>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision> =
        withContext(dispatchers.io) {
            dao.forOrigin(origin).mapNotNull { it.toDomainOrNull() }.associate { it.capability to it.decision }
        }

    override suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit> {
        if (!SiteOrigin.isSecure(origin)) {
            return Result.Error(IllegalArgumentException("Only secure origins are stored"))
        }
        return runIo {
            dao.upsert(
                SitePermissionEntity(
                    origin = origin,
                    capability = capability.storageValue,
                    decision = decision.storageValue,
                    decidedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun removeSite(origin: String): Result<Unit> = runIo { dao.deleteOrigin(origin) }

    override suspend fun clearAll(): Result<Unit> = runIo { dao.deleteAll() }

    @Suppress("TooGenericExceptionCaught") // A failed write is reported, never thrown into the UI.
    private suspend fun runIo(block: suspend () -> Unit): Result<Unit> = withContext(dispatchers.io) {
        try {
            block()
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
