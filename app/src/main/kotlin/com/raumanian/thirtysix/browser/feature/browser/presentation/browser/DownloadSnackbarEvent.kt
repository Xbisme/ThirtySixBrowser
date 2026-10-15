package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** One-shot download messages shown while the person is still on the page that started it. */
sealed class DownloadSnackbarEvent {

    data class Started(val fileName: String) : DownloadSnackbarEvent()

    /** The platform download service is unavailable; nothing was recorded. */
    data object ServiceUnavailable : DownloadSnackbarEvent()

    data object StoragePermissionDenied : DownloadSnackbarEvent()

    /** The platform will not show the dialog again, so the message points at system settings. */
    data object StoragePermissionPermanentlyDenied : DownloadSnackbarEvent()
}
