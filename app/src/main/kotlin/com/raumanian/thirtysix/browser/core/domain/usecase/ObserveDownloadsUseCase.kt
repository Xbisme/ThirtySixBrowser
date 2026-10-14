package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The persisted download records, newest first, without live status: records change rarely while
 * status changes every second, so [ResolveDownloadStatusUseCase] polls status separately.
 */
class ObserveDownloadsUseCase @Inject constructor(
    private val repository: DownloadsRepository,
) {
    operator fun invoke(): Flow<List<DownloadRecord>> = repository.observeAll()
}
