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
) {
    companion object {
        val EMPTY: TabsUiState = TabsUiState()
    }
}
