package com.raumanian.thirtysix.browser.feature.privacy.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.SiteProtectionExceptionDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SiteProtectionExceptionEntity
import com.raumanian.thirtysix.browser.core.data.util.runIoCatching
import com.raumanian.thirtysix.browser.core.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.domain.util.ApplicationScope
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.privacy.data.mapper.toDomain
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * WebView IO threads read [currentSites], an in-memory copy of `site_protection_exceptions` loaded
 * once and kept in step by every write made here, so it must stay a singleton.
 */
class ProtectionExceptionRepositoryImpl @Inject constructor(
    private val dao: SiteProtectionExceptionDao,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope appScope: CoroutineScope,
) : ProtectionExceptionRepository {

    @Volatile
    private var sites: Set<String> = emptySet()

    init {
        appScope.launch(dispatchers.io) {
            dao.observeAll().collect { rows -> sites = rows.mapTo(HashSet()) { it.site } }
        }
    }

    override fun observeAll(): Flow<List<SiteProtectionException>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun currentSites(): Set<String> = sites

    override suspend fun add(site: String): Result<Unit> = runIoCatching(dispatchers.io) {
        dao.upsert(SiteProtectionExceptionEntity(site = site, addedAt = System.currentTimeMillis()))
        sites = sites + site
    }

    override suspend fun remove(site: String): Result<Unit> = runIoCatching(dispatchers.io) {
        dao.delete(site)
        sites = sites - site
    }

    override suspend fun clearAll(): Result<Unit> = runIoCatching(dispatchers.io) {
        dao.deleteAll()
        sites = emptySet()
    }
}
