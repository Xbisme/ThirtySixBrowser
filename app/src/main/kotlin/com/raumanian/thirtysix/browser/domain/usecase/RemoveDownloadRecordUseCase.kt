package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Spec 015 FR-031 / FR-034b — take an entry out of the list, leaving the file alone.
 *
 * Refuses an in-flight entry. That is not defensive padding: removing the row discards the
 * transfer handle, which would leave a download running that the app can neither cancel nor
 * record on completion. The UI already withholds the action (FR-034a), and this is the
 * second lock on the same door — a caller that gets it wrong fails loudly rather than
 * silently orphaning a transfer.
 *
 * It cancels nothing. "Remove from list" that quietly stopped a download would turn a
 * tidying action into a destructive one.
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
