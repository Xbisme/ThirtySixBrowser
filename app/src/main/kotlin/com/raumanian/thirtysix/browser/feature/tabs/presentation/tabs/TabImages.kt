package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import java.io.File

/** Cached card images, read while a card is built; the caches' versions say when to read again. */
interface TabImages {
    fun favicon(url: String): File?

    fun screenshot(tabId: Long): File?
}

internal class CachedTabImages(
    private val faviconCache: FaviconCache,
    private val screenshotCache: ScreenshotCache,
) : TabImages {
    override fun favicon(url: String): File? = faviconCache.fileFor(url)

    override fun screenshot(tabId: Long): File? = screenshotCache.fileFor(tabId)
}
