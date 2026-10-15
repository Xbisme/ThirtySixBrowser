package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
/** Seam over the system clipboard, so ViewModels stay testable on the plain JVM. */
interface ClipboardWriter {
    /**
     * Android 13+ shows [label] in its clipboard preview, telling a download link from a page
     * address. Returns false when the service is unavailable or rejects the write.
     */
    fun copyUrl(url: String, label: String = AppConstants.CLIPBOARD_URL_LABEL): Boolean
}
