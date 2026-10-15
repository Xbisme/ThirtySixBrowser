package com.raumanian.thirtysix.browser.feature.downloads.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Stops an in-flight transfer; the platform discards the partial file, since the app may lack
 * write access to it. The row is removed too, or the forgotten handle would resolve it to Missing.
 * A transfer that already finished is left intact and reports no cancellation.
 */
class CancelDownloadUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val repository: DownloadsRepository,
) {

    /** @return true when a live transfer was actually cancelled. */
    suspend operator fun invoke(item: DownloadListItem): Boolean {
        // Re-read from the platform: the UI's snapshot may be one poll interval stale.
        val current = gateway.queryStatus(item.record.transferHandle)
        val stillCancellable = current != null && current.isInFlight

        return when {
            !stillCancellable -> false
            !gateway.cancel(item.record.transferHandle) -> false
            else -> {
                repository.deleteById(item.record.id)
                true
            }
        }
    }
}
