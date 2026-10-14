package com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local

import android.content.Context
import android.content.Intent
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

    override fun open(contentUri: String, mimeType: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri.toUri(), mimeType.ifEmpty { FALLBACK_MIME_TYPE })
            // Launched from a non-Activity context, so a new task is required; the read
            // grant is what lets the receiving app actually read the content URI.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private companion object {
        /** Lets the chooser offer anything when the server declared no usable type. */
        const val FALLBACK_MIME_TYPE = "*/*"
    }
}
