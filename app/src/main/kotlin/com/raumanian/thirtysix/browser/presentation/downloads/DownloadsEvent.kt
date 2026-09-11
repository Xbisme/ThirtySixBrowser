package com.raumanian.thirtysix.browser.presentation.downloads

import com.raumanian.thirtysix.browser.domain.model.DownloadListItem

/**
 * Spec 015 — one-shot events from the Downloads screen.
 *
 * Emitted on a dedicated `SharedFlow` with `replay = 0`, `extraBufferCapacity = 1` and
 * `DROP_OLDEST` — the channel policy Spec 013 set and Spec 014 mirrored. One-shot rather
 * than state because a snackbar that survives a rotation is a bug, not a feature.
 */
sealed class DownloadsEvent {

    /** FR-033 — the source address is on the clipboard. */
    data object LinkCopied : DownloadsEvent()

    /** FR-027 — nothing on the device can open this file type. */
    data object NoAppCanOpenFile : DownloadsEvent()

    /** FR-029 — the file is gone from disk; offer to clear the stale row. */
    data class FileMissing(val item: DownloadListItem) : DownloadsEvent()

    /** FR-028 — the entry has not finished, so there is nothing to open yet. */
    data object DownloadNotComplete : DownloadsEvent()

    /** FR-032 — the file was deleted at the user's request. */
    data object FileDeleted : DownloadsEvent()

    /** FR-031 — the row was removed; the file was left alone. */
    data object RecordRemoved : DownloadsEvent()

    /** FR-037 — the transfer was cancelled and its partial file discarded. */
    data object DownloadCancelled : DownloadsEvent()

    /** A platform call failed in a way that blocked what the user asked for (FR-055). */
    data object OperationFailed : DownloadsEvent()
}
