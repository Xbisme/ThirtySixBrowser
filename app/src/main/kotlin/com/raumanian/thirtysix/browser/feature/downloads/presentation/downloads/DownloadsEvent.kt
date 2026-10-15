package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
// One-shot rather than state: a snackbar that survives a rotation is a bug.
sealed class DownloadsEvent {

    data object LinkCopied : DownloadsEvent()

    data object NoAppCanOpenFile : DownloadsEvent()

    /** The file is gone from disk; offer to clear the stale row. */
    data class FileMissing(val item: DownloadListItem) : DownloadsEvent()

    data object DownloadNotComplete : DownloadsEvent()

    data object FileDeleted : DownloadsEvent()

    /** The row was removed; the file was left alone. */
    data object RecordRemoved : DownloadsEvent()

    /** The transfer was cancelled and its partial file discarded. */
    data object DownloadCancelled : DownloadsEvent()

    /** A platform call failed in a way that blocked what the user asked for. */
    data object OperationFailed : DownloadsEvent()
}
