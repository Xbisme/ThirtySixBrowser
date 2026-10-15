package com.raumanian.thirtysix.browser.core.data.datasource.local

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ClipboardWriter] backed by [ClipboardManager]. Wrapped in `runCatching` because some OEM builds
 * throw from clipboard writes while another app holds clipboard focus.
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
