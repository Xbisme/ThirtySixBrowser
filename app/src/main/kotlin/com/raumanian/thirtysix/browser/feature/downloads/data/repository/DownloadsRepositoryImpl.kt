package com.raumanian.thirtysix.browser.feature.downloads.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.DownloadRecordDao
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.downloads.data.mapper.DownloadRecordMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// Room only: it never touches the platform gateway, which use cases join in.
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

    override suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String) {
        withContext(dispatchers.io) { dao.updateCompletionMetadata(id, localUri, fileName) }
    }

    override suspend fun deleteById(id: Long): Boolean =
        withContext(dispatchers.io) { dao.deleteById(id) > 0 }

    override suspend fun count(): Int =
        withContext(dispatchers.io) { dao.count() }
}
