package com.raumanian.thirtysix.browser.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Spec 012 — derived signal: is the currently-active tab incognito?
 *
 * Drives:
 *  - The `FLAG_SECURE` lifecycle binding in `MainActivity` via
 *    [com.raumanian.thirtysix.browser.presentation.util.SecureWindowEffect]
 *    (FR-016 / FR-016a).
 *  - The address-bar incognito indicator
 *    ([com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState.isIncognito]).
 *  - The cache-write gate inside `BrowserViewModel.onIconReceived` /
 *    `onScreenshotReady` (FR-009 / FR-010).
 *
 * Source: head of [ObserveAllTabsUseCase] (the merged sort puts the most-
 * recently-active tab first). [distinctUntilChanged] avoids redundant
 * recomposition + redundant FLAG_SECURE flag flips.
 */
class ObserveActiveTabIsIncognitoUseCase @Inject constructor(
    private val observeAllTabs: ObserveAllTabsUseCase,
) {
    operator fun invoke(): Flow<Boolean> = observeAllTabs()
        .map { tabs -> tabs.firstOrNull()?.isIncognito ?: false }
        .distinctUntilChanged()
}
