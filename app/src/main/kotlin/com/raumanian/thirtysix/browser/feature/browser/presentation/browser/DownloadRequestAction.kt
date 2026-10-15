package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface DownloadRequestAction {
    data class Requested(
        val url: String,
        val userAgent: String,
        val contentDisposition: String?,
        val mimeType: String?,
    ) : DownloadRequestAction

    data class StoragePermissionDenied(val permanently: Boolean) : DownloadRequestAction
}
