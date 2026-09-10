package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.dao.HistoryDao
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.data.mapper.HistoryEntryMapper
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Spec 014 — implementation of [HistoryRepository]. Depends ONLY on the DAO and
 * a [DispatcherProvider] (no Repo→Repo per Constitution §IV).
 */
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao,
    private val dispatchers: DispatcherProvider,
) : HistoryRepository {

    override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long =
        withContext(dispatchers.io) {
            dao.insert(
                HistoryEntryEntity(
                    id = 0L,
                    url = url,
                    title = title,
                    visitedAt = visitedAt,
                ),
            )
        }

    override fun observeAll(): Flow<List<HistoryEntry>> =
        dao.observeAll().map { list -> list.map(HistoryEntryMapper::toDomain) }

    override suspend fun updateTitle(id: Long, title: String): Int =
        withContext(dispatchers.io) { dao.updateTitle(id, title) }

    override suspend fun deleteById(id: Long): Int =
        withContext(dispatchers.io) { dao.deleteById(id) }

    override suspend fun pruneOlderThan(cutoffMillis: Long): Int =
        withContext(dispatchers.io) {
            // `deleteInRange` is inclusive on both ends; epoch 0 as the lower bound covers
            // every row ever written, and `cutoffMillis - 1` keeps the cutoff itself alive.
            dao.deleteInRange(fromInclusive = 0L, toInclusive = cutoffMillis - 1L)
        }

    override suspend fun clearAll(): Int =
        withContext(dispatchers.io) { dao.deleteAll() }

    override suspend fun count(): Int =
        withContext(dispatchers.io) { dao.count() }
}
