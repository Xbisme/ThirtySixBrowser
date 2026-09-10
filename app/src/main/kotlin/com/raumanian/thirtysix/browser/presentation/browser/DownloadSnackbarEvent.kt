package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Spec 015 FR-002 / FR-008a / FR-012 — one-shot snackbar events raised while the user is
 * still on the page that triggered a download.
 *
 * Carried on [BrowserUiState] and cleared by `BrowserViewModel.consumeDownloadSnackbarEvent()`,
 * mirroring Spec 013's [BookmarkSnackbarEvent] rather than Spec 014's `SharedFlow` channel:
 * this ViewModel already established the in-state pattern, and mixing both in one class
 * would leave two ways to do the same thing.
 */
sealed class DownloadSnackbarEvent {

    /** FR-002 — the transfer was accepted; confirm immediately, naming the file. */
    data class Started(val fileName: String) : DownloadSnackbarEvent()

    /** FR-008a — the platform download service is unavailable; nothing was recorded. */
    data object ServiceUnavailable : DownloadSnackbarEvent()

    /** FR-012 — the user declined the storage permission, so the download did not start. */
    data object StoragePermissionDenied : DownloadSnackbarEvent()

    /**
     * FR-012 — the user declined permanently. The message must point at system settings
     * rather than re-prompting into a void, because the platform will no longer show the
     * dialog at all.
     */
    data object StoragePermissionPermanentlyDenied : DownloadSnackbarEvent()
}
