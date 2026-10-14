package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Closes a tab and deletes its screenshot; closing the last tab opens a fresh home tab.
 * Favicons stay: they are keyed by host and shared with other tabs.
 */
class CloseTabUseCase @Inject constructor(
    private val repository: TabRepository,
    private val screenshotCache: ScreenshotCache,
) {
    suspend operator fun invoke(tabId: Long) {
        // Screenshot first, so a tab re-created with the same id never shows the closed tab's image.
        screenshotCache.delete(tabId)
        repository.closeTab(tabId)
    }
}
