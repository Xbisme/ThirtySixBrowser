package com.raumanian.thirtysix.browser.presentation.browser.page

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import java.io.File

/**
 * Spec 021 research R3 — photos taken for a page's file upload, in `cacheDir/capture/`. Only the
 * newest is kept: older ones are deleted when the next capture starts and when the browser
 * screen goes away (not on every navigation — a page may still be uploading the file then).
 */
object CaptureFiles {
    private const val PREFIX = "capture-"
    private const val SUFFIX = ".jpg"

    private fun dir(context: Context): File = File(context.cacheDir, AppConstants.CAPTURE_CACHE_DIR)

    /** A fresh, empty file for the camera app, shared through the app's `FileProvider`. */
    fun newPhoto(context: Context): Pair<File, Uri>? = runCatching {
        clear(context)
        val folder = dir(context).apply { mkdirs() }
        val file = File.createTempFile(PREFIX, SUFFIX, folder)
        val authority = context.packageName + AppConstants.FILE_PROVIDER_AUTHORITY_SUFFIX
        file to FileProvider.getUriForFile(context, authority, file)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach(File::delete) }
    }
}
