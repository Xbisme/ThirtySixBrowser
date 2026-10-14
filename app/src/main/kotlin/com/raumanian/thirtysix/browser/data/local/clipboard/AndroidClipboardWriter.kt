package com.raumanian.thirtysix.browser.data.local.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    override fun copyUrl(url: String, label: String): Boolean =
        runCatching {
            val manager = context.getSystemService<ClipboardManager>() ?: return false
            manager.setPrimaryClip(ClipData.newPlainText(label, url))
            true
        }.getOrDefault(false)
}
