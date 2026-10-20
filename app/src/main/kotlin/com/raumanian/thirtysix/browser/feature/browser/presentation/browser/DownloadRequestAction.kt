package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface DownloadRequestAction {
    data class Requested(
        val url: String,
        val userAgent: String,
        val contentDisposition: String?,
        val mimeType: String?,
        val referer: String? = null,
        /** Never logged or stored. */
        val cookies: String? = null,
    ) : DownloadRequestAction

    data class StoragePermissionDenied(val permanently: Boolean) : DownloadRequestAction
}
