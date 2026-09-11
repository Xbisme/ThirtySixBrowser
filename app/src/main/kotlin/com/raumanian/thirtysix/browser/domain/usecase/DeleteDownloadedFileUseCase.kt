package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import javax.inject.Inject

/**
 * Spec 015 FR-032 — delete the downloaded file **and** its row, after the user confirms.
 *
 * Order matters: the file goes first, then the record. If the file delete fails the record
 * survives, so the entry stays visible and the user can try again — the alternative would
 * strand a file on disk with nothing in the app pointing at it.
 *
 * A file that is already gone counts as success. The user's intent — that it should not be
 * there — is satisfied either way, and reporting failure would only be confusing.
 */
class DeleteDownloadedFileUseCase @Inject constructor(
    private val gateway: DownloadManagerGateway,
    private val repository: DownloadsRepository,
) {

    suspend operator fun invoke(record: DownloadRecord): Boolean {
        // Deliberately NOT gated on `record.localUri`. An earlier version treated a null
        // URI as "no file to delete" and went straight to removing the row, which meant a
        // record whose URI had never been backfilled reported success while its file stayed
        // on disk. The transfer handle is always present (FR-008b), so it is the reliable
        // key — and the gateway verifies the file is actually gone before saying so.
        if (!gateway.deleteFile(record.transferHandle, record.fileName)) return false
        return repository.deleteById(record.id)
    }
}
