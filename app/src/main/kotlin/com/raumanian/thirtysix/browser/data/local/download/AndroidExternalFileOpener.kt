package com.raumanian.thirtysix.browser.data.local.download

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.domain.repository.ExternalFileOpener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [ExternalFileOpener].
 *
 * The URI handed in is always a **content** URI supplied by the platform download service.
 * A `file://` URI would throw `FileUriExposedException` on every supported device, since
 * minSdk here is 24 — which is why the gateway is the only source of these values and this
 * class never constructs a path of its own.
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
