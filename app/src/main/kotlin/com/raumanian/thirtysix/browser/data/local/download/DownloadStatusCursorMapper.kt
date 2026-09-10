package com.raumanian.thirtysix.browser.data.local.download

import android.app.DownloadManager
import android.database.Cursor
import com.raumanian.thirtysix.browser.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus

/**
 * Spec 015 — translation from the platform download service's cursor rows into
 * [DownloadStatus].
 *
 * Split out of [AndroidDownloadManagerGateway] so that class stays within detekt's
 * `TooManyFunctions` threshold, and because this is genuinely a separate concern: the
 * gateway decides *when* to talk to the platform, this decides what the platform's answer
 * means.
 */
internal object DownloadStatusCursorMapper {

    fun readAll(cursor: Cursor): Map<Long, DownloadStatus> {
        val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        if (idIndex < 0 || statusIndex < 0) return emptyMap()

        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
        val soFarIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

        val result = mutableMapOf<Long, DownloadStatus>()
        while (cursor.moveToNext()) {
            val soFar = if (soFarIndex >= 0) cursor.getLong(soFarIndex) else 0L
            // The platform reports -1 when the server declared no length; surfacing that as
            // null lets the UI show indeterminate progress instead of a fabricated share.
            val total = if (totalIndex >= 0) cursor.getLong(totalIndex).takeIf { it > 0 } else null
            val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else 0
            result[cursor.getLong(idIndex)] = toStatus(cursor.getInt(statusIndex), soFar, total, reason)
        }
        return result
    }

    private fun toStatus(status: Int, soFar: Long, total: Long?, reason: Int): DownloadStatus =
        when (status) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.Pending
            DownloadManager.STATUS_RUNNING -> DownloadStatus.Running(soFar, total)
            DownloadManager.STATUS_PAUSED -> DownloadStatus.Paused(soFar, total)
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Complete(total)
            DownloadManager.STATUS_FAILED -> DownloadStatus.Failed(toFailureCause(reason))
            else -> DownloadStatus.Failed(DownloadFailureCause.Generic)
        }

    private fun toFailureCause(reason: Int): DownloadFailureCause = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> DownloadFailureCause.InsufficientSpace
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_TOO_MANY_REDIRECTS,
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        DownloadManager.ERROR_CANNOT_RESUME,
        -> DownloadFailureCause.NetworkFailure
        // Every other platform reason collapses here rather than surfacing an error code
        // the user cannot act on (FR-022).
        else -> DownloadFailureCause.Generic
    }
}
