package com.raumanian.thirtysix.browser.data.local.download

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec 015 FR-025 – FR-027, FR-054 — narrow seam over handing a file to another app.
 *
 * The third platform seam in this feature, alongside `DownloadManagerGateway` and Spec 014's
 * `ClipboardWriter`. FR-054 names external-app launching explicitly, and for the same reason
 * as the others: it keeps `Intent` out of the domain and lets the open path be tested
 * without a device.
 *
 * Total, like its siblings — a device with no handler for a file type is an ordinary
 * outcome reported in the return value, not an exception (FR-027).
 */
interface ExternalFileOpener {

    /**
     * Open [contentUri] with whatever app handles [mimeType].
     *
     * @return false when nothing on the device can open it, or the platform refused. Never
     *   throws, and in particular never lets `ActivityNotFoundException` escape.
     */
    fun open(contentUri: String, mimeType: String): Boolean
}

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
