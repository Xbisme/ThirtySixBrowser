package com.raumanian.thirtysix.browser.presentation.downloads

import androidx.annotation.StringRes
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus

/**
 * Spec 015 — the presentation-side mapping from domain status to localized text.
 *
 * Lives here rather than on the sealed classes themselves so `domain/model/` stays free of
 * Android imports (Constitution §IV). This is the same separation Spec 007 made between
 * `ErrorReason` and its `toUserMessageRes()` extension.
 */
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

/**
 * FR-022 — a failure names a cause the user can act on where one is known. Anything the
 * user cannot do something about collapses into the generic message rather than exposing a
 * platform error code.
 */
@StringRes
fun DownloadFailureCause.toLabelRes(): Int = when (this) {
    DownloadFailureCause.InsufficientSpace -> R.string.downloads_failure_insufficient_space
    DownloadFailureCause.NetworkFailure -> R.string.downloads_failure_network
    DownloadFailureCause.Generic -> R.string.downloads_failure_generic
}

/**
 * Bytes transferred so far, or null when the status carries no byte counts.
 * Drives the size column while a transfer is live.
 */
val DownloadStatus.bytesSoFarOrNull: Long?
    get() = when (this) {
        is DownloadStatus.Running -> bytesSoFar
        is DownloadStatus.Paused -> bytesSoFar
        else -> null
    }

/**
 * Total size when the server declared one, else null.
 *
 * Null is meaningful: the UI must show **indeterminate** progress rather than inventing a
 * percentage against an unknown total.
 */
val DownloadStatus.totalBytesOrNull: Long?
    get() = when (this) {
        is DownloadStatus.Running -> totalBytes
        is DownloadStatus.Paused -> totalBytes
        is DownloadStatus.Complete -> totalBytes
        else -> null
    }

/** Fractional progress in `0f..1f`, or null when it cannot be known. */
val DownloadStatus.progressFractionOrNull: Float?
    get() {
        val total = totalBytesOrNull ?: return null
        val soFar = bytesSoFarOrNull ?: return null
        return if (total > 0L) (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    }
