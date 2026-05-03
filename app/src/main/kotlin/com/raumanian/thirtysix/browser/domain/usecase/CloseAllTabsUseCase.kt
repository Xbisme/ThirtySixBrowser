package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 011 — wipe every tab and create exactly one fresh home tab (US3 #4 /
 * FR-012). Atomic at the repository layer (Mutex-guarded — R5/R10).
 *
 * Triggered from the switcher's "Close all tabs" affordance after the user
 * confirms the destructive-action dialog (`CloseAllTabsConfirmDialog`).
 *
 * Spec 011 Q4 amendment (2026-05-03) — also wipes the entire screenshot
 * cache directory so disk usage drops to zero after close-all. The favicon
 * cache is intentionally NOT cleared (favicons are origin-scoped, useful
 * even after the tabs that loaded them are gone — they still apply when
 * the user revisits those origins).
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
