package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.dao.DownloadRecordDao
import com.raumanian.thirtysix.browser.data.mapper.DownloadRecordMapper
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Spec 015 — Room-backed [DownloadsRepository].
 *
 * Depends only on its DAO and the dispatcher provider — never on another repository, and
 * notably **never on the platform gateway** (Constitution §IV). See the interface KDoc for
 * why that separation is deliberate.
 */
class DownloadsRepositoryImpl @Inject constructor(
    private val dao: DownloadRecordDao,
    private val dispatchers: DispatcherProvider,
) : DownloadsRepository {

    override suspend fun insert(record: DownloadRecord): Long =
        withContext(dispatchers.io) { dao.insert(DownloadRecordMapper.toEntity(record)) }

    override fun observeAll(): Flow<List<DownloadRecord>> =
        dao.observeAll().map { list -> list.map(DownloadRecordMapper::toDomain) }

    override suspend fun getById(id: Long): DownloadRecord? =
        withContext(dispatchers.io) { dao.getById(id)?.let(DownloadRecordMapper::toDomain) }

    override suspend fun updateLocalUri(id: Long, localUri: String) {
        withContext(dispatchers.io) { dao.updateLocalUri(id, localUri) }
    }

    override suspend fun deleteById(id: Long): Boolean =
        withContext(dispatchers.io) { dao.deleteById(id) > 0 }

    override suspend fun count(): Int =
        withContext(dispatchers.io) { dao.count() }
}
