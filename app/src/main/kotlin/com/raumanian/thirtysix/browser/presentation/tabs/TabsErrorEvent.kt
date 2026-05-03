package com.raumanian.thirtysix.browser.presentation.tabs

/**
 * Spec 011 — one-shot transient event surface for the tab-management feature.
 *
 * Used by both [TabsUiState.errorEvent] (switcher's "new tab" card cap-reached)
 * AND [com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState.tabsEvent]
 * (BrowserScreen long-press on the 5th BottomAppBar button cap-reached). One
 * shared sealed type so both surfaces have a single semantic for the
 * cap-reached snackbar message.
 *
 * Lives in its own file (not inside `TabsUiState.kt`) so detekt's
 * `MatchingDeclarationName` rule does not fire.
 */
sealed class TabsErrorEvent {
    /**
     * Emitted when [com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase]
     * returns `Result.Error(MaxTabsReachedException())`. Consumers display the
     * localized `R.string.browser_max_tabs_reached` text in a snackbar then
     * call `consumeXxx()` to clear the event.
     */
    data object MaxTabsReached : TabsErrorEvent()
}
