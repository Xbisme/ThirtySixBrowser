package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap

/**
 * Spec 021 research R3 — the intents a page's file input opens, and how their result becomes
 * the page's answer. Pure intent building, so it is tested without a WebView.
 */
object FileChooserIntents {
    private const val ANY_TYPE = "*/*"
    private const val IMAGE_PREFIX = "image/"
    private const val VIDEO_PREFIX = "video/"

    /** The page's accept list as MIME types: extensions (".pdf") mapped, blanks dropped. */
    fun mimeTypes(request: FileChooserRequest): List<String> = request.acceptTypes
        .flatMap { it.split(',') }
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .mapNotNull { type ->
            if (type.startsWith(".")) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.removePrefix("."))
            } else {
                type
            }
        }
        .distinct()

    /** The system picker for [request] (FR-011): its accept types and, when asked, several files. */
    fun picker(request: FileChooserRequest): Intent {
        val types = mimeTypes(request)
        return Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(types.singleOrNull() ?: ANY_TYPE)
            .apply {
                if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                if (request.allowMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
    }

    /** Whether the camera is worth offering: the page asked for capture, or accepts only photos/videos. */
    fun wantsCapture(request: FileChooserRequest): Boolean {
        val types = mimeTypes(request)
        val mediaOnly = types.isNotEmpty() && types.all { it.startsWith(IMAGE_PREFIX) || it.startsWith(VIDEO_PREFIX) }
        return request.captureRequested || mediaOnly
    }

    /**
     * Camera intents for [request] (FR-013): a photo written to [photoOutput] when images are
     * accepted, a video (returned by the camera app) when videos are accepted.
     */
    fun captures(request: FileChooserRequest, photoOutput: Uri): List<Intent> {
        val types = mimeTypes(request)
        val acceptsAll = types.isEmpty() || ANY_TYPE in types
        val photo = acceptsAll || types.any { it.startsWith(IMAGE_PREFIX) }
        val video = acceptsAll || types.any { it.startsWith(VIDEO_PREFIX) }
        return listOfNotNull(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, photoOutput)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .takeIf { photo },
            Intent(MediaStore.ACTION_VIDEO_CAPTURE).takeIf { video },
        )
    }

    /** The picker alone, or a chooser that also offers [captures]. */
    fun chooser(picker: Intent, captures: List<Intent>, title: String): Intent =
        if (captures.isEmpty()) {
            picker
        } else {
            Intent.createChooser(picker, title).putExtra(Intent.EXTRA_INITIAL_INTENTS, captures.toTypedArray())
        }

    /**
     * The files to hand the page (FR-012): what the picker returned, else the photo when the
     * camera wrote one, else `null` (cancel).
     */
    fun result(resultCode: Int, data: Intent?, photoOutput: Uri?, photoWritten: Boolean): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK) return null
        val clip = data?.clipData
        val picked = buildList {
            if (clip != null) (0 until clip.itemCount).mapNotNullTo(this) { clip.getItemAt(it).uri }
            data?.data?.let { if (it !in this) add(it) }
        }
        return when {
            picked.isNotEmpty() -> picked.toTypedArray()
            photoOutput != null && photoWritten -> arrayOf(photoOutput)
            else -> null
        }
    }
}
