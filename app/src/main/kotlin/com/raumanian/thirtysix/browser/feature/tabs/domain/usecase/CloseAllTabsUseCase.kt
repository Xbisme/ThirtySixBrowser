package com.raumanian.thirtysix.browser.feature.tabs.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Replaces every tab with one fresh home tab and wipes the screenshot cache. Favicons stay:
 * they are keyed by origin and still apply when the person revisits those sites.
 */
class CloseAllTabsUseCase @Inject constructor(
    private val repository: TabRepository,
    private val screenshotCache: ScreenshotCache,
) {
    suspend operator fun invoke() {
        screenshotCache.clearAll()
        repository.closeAllTabs()
    }
}
