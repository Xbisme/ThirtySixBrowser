package com.raumanian.thirtysix.browser.feature.downloads.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Takes an entry out of the list, leaving the file alone and cancelling nothing.
 * Refuses an in-flight entry: dropping its row would orphan a running transfer the app can no
 * longer cancel or record, so a caller that gets this wrong fails loudly.
 */
class RemoveDownloadRecordUseCase @Inject constructor(
    private val repository: DownloadsRepository,
) {

    /** @return true when the row was removed; false when it was in flight or already gone. */
    suspend operator fun invoke(item: DownloadListItem): Boolean {
        if (item.status.isInFlight) return false
        return repository.deleteById(item.record.id)
    }
}
