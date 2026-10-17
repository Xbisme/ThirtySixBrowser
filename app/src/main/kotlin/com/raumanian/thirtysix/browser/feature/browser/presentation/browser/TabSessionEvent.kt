package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface TabSessionEvent {
    /** History was cleared: the shown page keeps its place but loses its back and forward. */
    data object ClearLiveHistory : TabSessionEvent
}
