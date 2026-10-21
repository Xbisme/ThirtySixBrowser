package com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.domain.repository.ExternalFileOpener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The URI is always a platform content URI: a `file://` URI throws `FileUriExposedException`
 * on every supported API level, so this class never builds a path.
 */
@Singleton
class AndroidExternalFileOpener @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExternalFileOpener {

    override fun open(contentUri: String, mimeType: String, fileName: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri.toUri(), resolveType(mimeType, fileName))
            // A non-Activity context needs a new task; the read grant lets the other app open the URI.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun resolveType(mimeType: String, fileName: String): String =
        mimeType.takeUnless { it.isBlank() || it.lowercase() in GENERIC_BINARY_TYPES }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            ?: FALLBACK_MIME_TYPE

    private companion object {
        /** Lets the chooser offer anything when the server declared no usable type. */
        const val FALLBACK_MIME_TYPE = "*/*"

        val GENERIC_BINARY_TYPES = setOf(
            "application/octet-stream",
            "binary/octet-stream",
            "application/force-download",
            "application/x-download",
            "application/download",
        )
    }
}
