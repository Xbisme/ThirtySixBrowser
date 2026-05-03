package com.raumanian.thirtysix.browser.presentation.tabs

import com.raumanian.thirtysix.browser.domain.model.Tab

/**
 * Immutable UI snapshot for the tab switcher screen (Spec 011).
 *
 * - [tabs] — full tab list, ordered by `lastActiveAt` DESC then `id` ASC
 *   (FR-014 / R1 / H1). Sourced from
 *   [com.raumanian.thirtysix.browser.domain.usecase.ObserveTabsUseCase].
 * - [activeTabId] — derived as `tabs.maxByOrNull { it.lastActiveAt }?.id`.
 *   Drives the active-tab visual indicator on each `TabSwitcherCard`.
 * - [isCloseAllDialogVisible] — UI-only flag toggled by
 *   `TabsViewModel.onCloseAllRequested()` / `onCloseAllDismissed()` /
 *   `onCloseAllConfirmed()` (FR-012).
 * - [errorEvent] — one-shot transient surface for the cap-reached snackbar
 *   (FR-016). Cleared by `TabsViewModel.consumeErrorEvent()` after display.
 */
data class TabsUiState(
    val tabs: List<Tab> = emptyList(),
    val activeTabId: Long? = null,
    val isCloseAllDialogVisible: Boolean = false,
    val errorEvent: TabsErrorEvent? = null,
    /**
     * Spec 012 — derived from [tabs] as `tabs.count { it.isIncognito }`.
     * Drives the visibility logic of the "Close all incognito" affordance
     * (US5 acceptance scenarios 1+2 — hidden iff this is 0).
     */
    val incognitoTabCount: Int = 0,
    /**
     * Spec 012 — UI-only flag toggled by `TabsViewModel.onCloseAllIncognitoRequested()`,
     * `onCloseAllIncognitoDismissed()`, and `onCloseAllIncognitoConfirmed()`.
     * Drives the "Close all incognito" confirmation dialog visibility.
     */
    val isCloseAllIncognitoDialogVisible: Boolean = false,
) {
    companion object {
        val EMPTY: TabsUiState = TabsUiState()
    }
}
