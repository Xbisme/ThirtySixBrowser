package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent

sealed interface TabsEvent {
    data object ShowBrowser : TabsEvent

    data class Error(val error: TabsErrorEvent) : TabsEvent
}
