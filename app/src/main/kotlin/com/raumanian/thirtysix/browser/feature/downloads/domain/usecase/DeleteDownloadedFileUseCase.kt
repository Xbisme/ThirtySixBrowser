package com.raumanian.thirtysix.browser.feature.downloads.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Deletes the file first, then the record, so a failed file delete leaves the entry visible
 * for a retry. A file that is already gone counts as success.
 */
class DeleteDownloadedFileUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val repository: DownloadsRepository,
) {

    suspend operator fun invoke(record: DownloadRecord): Boolean {
        // Keyed on the transfer handle, not `localUri`: a record whose URI was never backfilled
        // would otherwise report success while its file stayed on disk.
        if (!gateway.deleteFile(record.transferHandle, record.fileName)) return false
        return repository.deleteById(record.id)
    }
}
