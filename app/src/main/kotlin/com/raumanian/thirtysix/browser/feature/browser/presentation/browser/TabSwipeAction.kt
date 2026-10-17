package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface TabSwipeAction {
    /** [announce] is set by the accessibility actions, which have no slide to show the change. */
    data class SwitchTo(val tabId: Long, val announce: Boolean) : TabSwipeAction
}
