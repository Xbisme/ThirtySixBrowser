package com.raumanian.thirtysix.browser.data.local.download

import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import java.io.File

/**
 * Spec 015 — answers the one question the FR-024a fallback and FR-029 both rest on: is this
 * download's file still there?
 *
 * Split out of [AndroidDownloadManagerGateway] for the same reason [DownloadStatusCursorMapper]
 * is its own file — to keep that class under detekt's function threshold — but it earns the
 * separation on its own: presence is a filesystem question, not a download-service one.
 */
internal object DownloadedFileProbe {

    /** Where [AndroidDownloadManagerGateway] asks the platform to put every download. */
    fun publicDownloadFile(fileName: String): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        fileName,
    )

    /**
     * The **real file is checked first**, and the recorded content URI only as a fallback.
     *
     * That order is the whole point of this function. The recorded URI belongs to the
     * platform's download provider, so it stops resolving at the exact moment the provider
     * forgets the transfer — which is precisely the situation FR-024a's fallback exists for.
     * Checking the URI first made the "present → Complete" branch unreachable: after the
     * provider's data was cleared, every download reported `Missing` with all ten files
     * sitting untouched in the Downloads folder. Found at gate G8.
     *
     * The URI check is kept as a fallback because it is the only thing that can still answer
     * for a file the platform placed somewhere other than the public Downloads directory.
     */
    fun exists(context: Context, localUri: String?, fileName: String): Boolean = runCatching {
        publicDownloadFile(fileName).exists() || isUriReadable(context, localUri)
    }.getOrDefault(false)

    private fun isUriReadable(context: Context, uri: String?): Boolean = runCatching {
        val descriptor = uri?.let {
            context.contentResolver.openFileDescriptor(it.toUri(), FILE_MODE_READ)
        }
        descriptor?.use { true } == true
    }.getOrDefault(false)

    private const val FILE_MODE_READ = "r"
}
