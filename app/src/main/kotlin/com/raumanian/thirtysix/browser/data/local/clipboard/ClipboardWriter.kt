package com.raumanian.thirtysix.browser.data.local.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec 014 FR-021 — narrow seam over the Android system clipboard.
 *
 * Introduced so [com.raumanian.thirtysix.browser.presentation.history.HistoryViewModel]
 * can offer "Copy URL" without holding an Android `Context`, keeping its unit tests on
 * the plain JVM (no Robolectric). Mirrors the
 * [com.raumanian.thirtysix.browser.data.local.cache.FaviconCache] pattern from Spec 011:
 * interface + concrete implementation in one file, bound in
 * [com.raumanian.thirtysix.browser.di.ClipboardModule].
 *
 * > Deviates from tasks.md T067, which called for injecting `@ApplicationContext Context`
 * > straight into the ViewModel. The seam is behaviourally identical and strictly more
 * > testable, so the interface was preferred.
 */
interface ClipboardWriter {
    /**
     * Place [url] on the system clipboard as plain text.
     *
     * @return `true` when the write reached the platform clipboard, `false` when the
     *  service was unavailable or the platform rejected the write. Callers surface a
     *  confirmation snackbar only on `true` — on Android 13+ the system shows its own
     *  clipboard preview, but the in-app confirmation still covers older releases.
     */
    fun copyUrl(url: String): Boolean
}

/**
 * Production [ClipboardWriter] backed by [ClipboardManager].
 *
 * Every platform call is wrapped in `runCatching` — the same crash-resilience posture
 * Spec 012's `CookieJarSnapshotManager` adopted for system-service calls, since OEM
 * builds have been observed to throw from clipboard writes when another app holds
 * clipboard focus.
 */
@Singleton
class AndroidClipboardWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ClipboardWriter {

    override fun copyUrl(url: String): Boolean =
        runCatching {
            val manager = context.getSystemService<ClipboardManager>() ?: return false
            manager.setPrimaryClip(ClipData.newPlainText(AppConstants.CLIPBOARD_URL_LABEL, url))
            true
        }.getOrDefault(false)
}
