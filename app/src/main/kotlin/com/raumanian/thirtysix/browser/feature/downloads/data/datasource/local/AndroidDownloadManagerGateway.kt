package com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRequest
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    /**
     * Presence is checked against the **real file** first, and only then against the
     * content URI.
     *
     * The order is the whole point. The recorded URI belongs to the platform's download
     * provider, so it stops resolving at the exact moment the provider forgets the
     * transfer — which is precisely the situation FR-024a's fallback is for. Checking the
     * URI first made that branch unreachable: every forgotten download reported `Missing`
     * even with the file sitting in the Downloads folder. Found at gate G8.
     */
    override suspend fun fileExists(localUri: String?, fileName: String): Boolean =
        DownloadedFileProbe.exists(context, localUri, fileName)

    override suspend fun resolvedFileNameFor(transferHandle: Long): String? = runCatching {
        val manager = downloadManager ?: return null
        manager.query(buildQuery(listOf(transferHandle)))?.use { cursor ->
            val index = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (index < 0 || !cursor.moveToFirst()) return null
            cursor.getString(index)?.toUri()?.lastPathSegment
        }
    }.getOrNull()

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
            val file = DownloadedFileProbe.publicDownloadFile(fileName)
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
        const val DOWNLOAD_PROVIDER_PACKAGE = "com.android.providers.downloads"
        val DISABLED_STATES = setOf(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        )
    }
}
