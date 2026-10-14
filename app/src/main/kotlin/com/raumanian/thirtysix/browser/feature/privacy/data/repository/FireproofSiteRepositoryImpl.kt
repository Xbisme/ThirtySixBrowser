package com.raumanian.thirtysix.browser.feature.privacy.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.FireproofSiteDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
import com.raumanian.thirtysix.browser.core.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.privacy.data.mapper.toDomain
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spec 024 — [FireproofSiteRepository] over Room (`fireproof_sites`, schema v5).
 *
 * [currentSites] is an in-memory copy of the table, filled from the database once and then kept in
 * step by every write made here, as Spec 023's exception repository. Bound as a singleton.
 */
class FireproofSiteRepositoryImpl @Inject constructor(
    private val dao: FireproofSiteDao,
    private val dispatchers: DispatcherProvider,
) : FireproofSiteRepository {

    @Volatile
    private var sites: Set<String> = emptySet()

    init {
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            dao.observeAll().collect { rows -> sites = rows.mapTo(HashSet()) { it.site } }
        }
    }

    override fun observeAll(): Flow<List<FireproofSite>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun currentSites(): Set<String> = sites

    override suspend fun add(site: String): Result<Unit> = runIo {
        dao.upsert(FireproofSiteEntity(site = site, addedAt = System.currentTimeMillis()))
        sites = sites + site
    }

    override suspend fun remove(site: String): Result<Unit> = runIo {
        dao.delete(site)
        sites = sites - site
    }

    override suspend fun clearAll(): Result<Unit> = runIo {
        dao.deleteAll()
        sites = emptySet()
    }

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
