package com.raumanian.thirtysix.browser.data.local.download

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec 015 FR-054 — narrow seam over the Android system download service.
 *
 * Exists so download decision logic is unit-testable on the plain JVM and `presentation/`
 * holds no Android imports. Mirrors the
 * [com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter] pattern from
 * Spec 014: interface plus concrete implementation in one file, Hilt-bound.
 *
 * **Every method is total.** Per FR-055 (Spec 012's system-service posture) each platform
 * call is defensively wrapped, so a service that is missing, disabled by the user, or
 * throwing can never crash the browser. Failure is expressed in the return type, never as
 * a propagating exception — hence [enqueue] returning a nullable handle rather than
 * throwing, and [queryStatus] returning null for "not known" rather than signalling an
 * error. Not crashing is the floor, not the goal: callers MUST surface a localized message
 * whenever a failure blocks something the user asked for (FR-008a).
 */
interface DownloadManagerGateway {

    /**
     * Hand a download to the platform service, asking it to show its own progress and
     * completion notifications (FR-047). This app never composes a notification.
     *
     * @return the transfer handle, or **null** when the service is unavailable — disabled,
     *   absent, or refusing. A null return MUST produce a localized message and MUST NOT
     *   create a record (FR-008a, FR-008b).
     */
    suspend fun enqueue(request: DownloadRequest): Long?

    /**
     * Read the live state of one transfer.
     *
     * @return the status, or **null** when the service does not recognise the handle. Null
     *   is an ordinary, expected outcome — the service prunes its own records on its own
     *   schedule — resolved by callers via the file-presence fallback in FR-024a. It is
     *   never an error, and this never throws on an unknown handle.
     */
    suspend fun queryStatus(transferHandle: Long): DownloadStatus?

    /**
     * Read many transfers in one round trip.
     *
     * The result contains an entry only for handles still recognised; an absent key means
     * the same as a null from [queryStatus]. Exists so the list resolves N rows with one
     * query rather than N (FR-021, SC-006).
     */
    suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus>

    /**
     * Cancel an in-flight transfer, discarding any partially written file (FR-037).
     *
     * Cancelling an already-completed transfer MUST leave the finished file intact and
     * MUST NOT report a false cancellation (FR-039).
     */
    suspend fun cancel(transferHandle: Long): Boolean

    /**
     * The content URI for a completed download, suitable for handing to another app.
     *
     * Always a content URI, never a filesystem path: since minSdk 24, exposing a `file://`
     * URI across an app boundary throws, so a raw path is fatal on every supported device
     * (FR-026).
     */
    suspend fun contentUriFor(transferHandle: Long): String?

    /**
     * Whether the file behind a recorded content URI is still present and readable.
     *
     * Backs both FR-029 (deleted outside the app) and the FR-024a fallback (handle
     * forgotten, so presence decides between Complete and Missing).
     */
    suspend fun fileExists(localUri: String): Boolean

    /**
     * Delete the downloaded file itself (FR-032), after the user has confirmed.
     *
     * Takes the transfer handle rather than a URI because the platform's own `remove` is
     * what actually deletes the file; a content URI only addresses the provider's row.
     *
     * @return true only when the file is **verified gone**. Reporting success without
     *   checking is how this shipped a "File deleted" message over a file that was still
     *   on disk — see the note on the implementation.
     */
    suspend fun deleteFile(transferHandle: Long, fileName: String): Boolean

    /** Whether the platform download service is present and usable right now (FR-008a). */
    suspend fun isAvailable(): Boolean
}

/**
 * Production [DownloadManagerGateway] backed by [DownloadManager].
 */
@Singleton
class AndroidDownloadManagerGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DownloadManagerGateway {

    private val downloadManager: DownloadManager?
        get() = runCatching { context.getSystemService<DownloadManager>() }.getOrNull()

    override suspend fun enqueue(request: DownloadRequest): Long? = runCatching {
        val manager = downloadManager ?: return null
        val platformRequest = DownloadManager.Request(request.sourceUrl.toUri()).apply {
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.fileName)
            setTitle(request.fileName)
            if (request.mimeType.isNotEmpty()) setMimeType(request.mimeType)
            if (request.userAgent.isNotEmpty()) addRequestHeader(HEADER_USER_AGENT, request.userAgent)
            request.referer?.takeIf { it.isNotEmpty() }?.let { addRequestHeader(HEADER_REFERER, it) }
            // FR-047 — the platform renders progress and completion; this app composes none.
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        manager.enqueue(platformRequest)
    }.getOrNull()

    override suspend fun queryStatus(transferHandle: Long): DownloadStatus? =
        queryStatuses(listOf(transferHandle))[transferHandle]

    override suspend fun queryStatuses(transferHandles: List<Long>): Map<Long, DownloadStatus> =
        when {
            transferHandles.isEmpty() -> emptyMap()
            else -> runCatching {
                val manager = downloadManager
                when (manager) {
                    null -> emptyMap()
                    else -> manager.query(buildQuery(transferHandles))
                        ?.use(DownloadStatusCursorMapper::readAll)
                        .orEmpty()
                }
            }.getOrDefault(emptyMap())
        }

    override suspend fun cancel(transferHandle: Long): Boolean = runCatching {
        val manager = downloadManager ?: return false
        manager.remove(transferHandle) > 0
    }.getOrDefault(false)

    override suspend fun contentUriFor(transferHandle: Long): String? = runCatching {
        downloadManager?.getUriForDownloadedFile(transferHandle)?.toString()
    }.getOrNull()

    override suspend fun fileExists(localUri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(localUri.toUri(), FILE_MODE_READ)
            ?.use { true } ?: false
    }.getOrDefault(false)

    /**
     * Deleting used to be `contentResolver.delete(localUri)` and to return whatever row
     * count that produced. On an API 24 emulator that returned 1 — the provider's row was
     * removed — while the file itself stayed in the public Downloads folder, so the app
     * showed "File deleted" over a file that was still there. Two fixes, both necessary:
     * ask the platform to `remove` the download (which is what actually deletes the file it
     * manages), and then **verify** rather than trust the return value.
     */
    override suspend fun deleteFile(transferHandle: Long, fileName: String): Boolean =
        runCatching {
            downloadManager?.remove(transferHandle)
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName,
            )
            if (file.exists()) file.delete()
            // The only answer that matters: is it gone?
            !file.exists()
        }.getOrDefault(false)

    override suspend fun isAvailable(): Boolean = runCatching {
        downloadManager != null && !isDownloadProviderDisabled()
    }.getOrDefault(false)

    /**
     * `setFilterById` is vararg-only on the platform side, so the spread is forced; the
     * copy it makes is of at most a few hundred longs (A14's envelope) once per poll.
     */
    @Suppress("SpreadOperator")
    private fun buildQuery(transferHandles: List<Long>): DownloadManager.Query =
        DownloadManager.Query().setFilterById(*transferHandles.toLongArray())

    private fun isDownloadProviderDisabled(): Boolean = runCatching {
        context.packageManager.getApplicationEnabledSetting(DOWNLOAD_PROVIDER_PACKAGE) in DISABLED_STATES
    }.getOrDefault(false)

    private companion object {
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_REFERER = "Referer"
        const val FILE_MODE_READ = "r"
        const val DOWNLOAD_PROVIDER_PACKAGE = "com.android.providers.downloads"
        val DISABLED_STATES = setOf(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        )
    }
}
