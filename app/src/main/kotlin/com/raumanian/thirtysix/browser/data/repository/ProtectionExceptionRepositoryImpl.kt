package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.SiteProtectionExceptionDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SiteProtectionExceptionEntity
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.data.mapper.toDomain
import com.raumanian.thirtysix.browser.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.domain.repository.ProtectionExceptionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spec 023 — [ProtectionExceptionRepository] over Room (`site_protection_exceptions`, schema v4).
 *
 * WebView IO threads read [currentSites], an in-memory copy of the table: it is filled from the
 * database once, then kept in step by every write made here (research R11). Bound as a singleton.
 */
class ProtectionExceptionRepositoryImpl @Inject constructor(
    private val dao: SiteProtectionExceptionDao,
    private val dispatchers: DispatcherProvider,
) : ProtectionExceptionRepository {

    @Volatile
    private var sites: Set<String> = emptySet()

    init {
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            dao.observeAll().collect { rows -> sites = rows.mapTo(HashSet()) { it.site } }
        }
    }

    override fun observeAll(): Flow<List<SiteProtectionException>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun currentSites(): Set<String> = sites

    override suspend fun add(site: String): Result<Unit> = runIo {
        dao.upsert(SiteProtectionExceptionEntity(site = site, addedAt = System.currentTimeMillis()))
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
