package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 011 — write-through bridge from `BrowserViewModel`'s WebView callbacks
 * (`onUrlChanged`, `onLoadFinished`, `onTitleReceived`) to [TabRepository]
 * (FR-022 / R4).
 *
 * The use-case-coordination layer is the project's accepted seam for cross-
 * feature wiring (Constitution §IV). `BrowserViewModel` injects this use case
 * instead of `TabRepository` directly so the `browser/` feature stays
 * decoupled from the `tabs/` data layer.
 */
class UpdateActiveTabUrlAndTitleUseCase @Inject constructor(
    private val repository: TabRepository,
) {
    suspend operator fun invoke(tabId: Long, url: String, title: String) =
        repository.updateTabUrlAndTitle(tabId, url, title)
}
