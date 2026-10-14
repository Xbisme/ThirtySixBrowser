package com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local

import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import java.io.File

// Is a download's file still on disk? A filesystem question, kept apart from the download service.
internal object DownloadedFileProbe {

    fun publicDownloadFile(fileName: String): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        fileName,
    )

    /**
     * Checks the real file first and the recorded content URI only as a fallback: the URI belongs
     * to the download provider and stops resolving exactly when the provider forgets the transfer.
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
