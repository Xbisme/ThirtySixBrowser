package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Spec 015 FR-019 / FR-023 — observe the persisted half of the list, newest first.
 *
 * Deliberately does **not** resolve live status. Records change rarely (an insert per
 * download, a delete per removal) while status changes every second during a transfer;
 * keeping them on separate clocks is what lets the ViewModel re-poll status without
 * re-reading the database. [ResolveDownloadStatusUseCase] supplies the other half.
 */
class ObserveDownloadsUseCase @Inject constructor(
    private val repository: DownloadsRepository,
) {
    operator fun invoke(): Flow<List<DownloadRecord>> = repository.observeAll()
}
