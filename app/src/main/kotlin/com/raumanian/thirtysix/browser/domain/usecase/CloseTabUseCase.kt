package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 011 — close [tabId] (US3 / FR-024).
 *
 * Durable: once this returns, the row is gone from `TabDao`. If the post-
 * delete count would drop to 0, a fresh home tab is auto-created in the
 * same coroutine (FR-019 / R5 / US3 #3). The new active tab is the
 * most-recently-active surviving tab (US3 #2 / R1).
 *
 * Spec 011 Q4 amendment (2026-05-03) — also deletes the cached screenshot
 * file for [tabId] so disk usage stays bounded by `MAX_TABS × ~5 KB`. The
 * favicon cache is hostname-keyed and shared across tabs; it is NOT
 * cleared here (other tabs on the same host may still need it).
 */
class CloseTabUseCase @Inject constructor(
    private val repository: TabRepository,
    private val screenshotCache: ScreenshotCache,
) {
    suspend operator fun invoke(tabId: Long) {
        // Delete screenshot first so a brief race where the user re-opens a
        // tab with the same id (auto-recreate path in `closeTab`) does not
        // leak a stale screenshot from the closed tab.
        screenshotCache.delete(tabId)
        repository.closeTab(tabId)
    }
}
