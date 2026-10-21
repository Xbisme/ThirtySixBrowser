package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import kotlinx.coroutines.flow.Flow

/**
 * The persisted half of downloads: identity and durable metadata, never live transfer state,
 * which comes from [DownloadManagerGateway] and is joined in the use cases.
 * A row exists only for a transfer the platform accepted, so an unknown handle always means
 * forgotten, never never-started. Incognito downloads are recorded like any other.
 */
interface DownloadsRepository {

    /** Call only once the platform returned a handle; `fileName` must already be sanitised. */
    suspend fun insert(record: DownloadRecord): Long

    /** Most recent first; re-emits on every change, so downloads started elsewhere appear. */
    fun observeAll(): Flow<List<DownloadRecord>>

    suspend fun getById(id: Long): DownloadRecord?

    /** The stored name can differ from the requested one: the platform resolves collisions. */
    suspend fun updateCompletionMetadata(id: Long, localUri: String, fileName: String)

    /** Leaves the file untouched and cancels nothing; callers offer removal only for finished entries. */
    suspend fun deleteById(id: Long): Boolean
}
