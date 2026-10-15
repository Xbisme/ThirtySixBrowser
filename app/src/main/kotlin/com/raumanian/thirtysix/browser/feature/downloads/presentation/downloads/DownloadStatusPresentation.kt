package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import androidx.annotation.StringRes
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
// Status-to-text mapping lives here so the domain models stay free of Android imports.
@StringRes
fun DownloadStatus.toLabelRes(): Int = when (this) {
    DownloadStatus.Pending -> R.string.downloads_state_pending
    is DownloadStatus.Running -> R.string.downloads_state_downloading
    is DownloadStatus.Paused -> R.string.downloads_state_paused
    is DownloadStatus.Complete -> R.string.downloads_state_complete
    DownloadStatus.Cancelled -> R.string.downloads_state_cancelled
    DownloadStatus.Missing -> R.string.downloads_state_missing
    is DownloadStatus.Failed -> cause.toLabelRes()
}

// Causes the user cannot act on collapse into the generic message instead of a platform code.
@StringRes
fun DownloadFailureCause.toLabelRes(): Int = when (this) {
    DownloadFailureCause.InsufficientSpace -> R.string.downloads_failure_insufficient_space
    DownloadFailureCause.NetworkFailure -> R.string.downloads_failure_network
    DownloadFailureCause.Generic -> R.string.downloads_failure_generic
}

val DownloadStatus.bytesSoFarOrNull: Long?
    get() = when (this) {
        is DownloadStatus.Running -> bytesSoFar
        is DownloadStatus.Paused -> bytesSoFar
        else -> null
    }

// Null when the server declared no length: the UI then shows indeterminate progress.
val DownloadStatus.totalBytesOrNull: Long?
    get() = when (this) {
        is DownloadStatus.Running -> totalBytes
        is DownloadStatus.Paused -> totalBytes
        is DownloadStatus.Complete -> totalBytes
        else -> null
    }

val DownloadStatus.progressFractionOrNull: Float?
    get() {
        val total = totalBytesOrNull ?: return null
        val soFar = bytesSoFarOrNull ?: return null
        return if (total > 0L) (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    }
