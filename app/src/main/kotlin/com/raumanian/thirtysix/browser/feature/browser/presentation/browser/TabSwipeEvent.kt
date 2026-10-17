package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface TabSwipeEvent {
    data class Announce(val preview: TabPreview) : TabSwipeEvent
}
