package com.raumanian.thirtysix.browser.feature.browser.presentation.home

sealed interface HomeAction {
    data object Resumed : HomeAction

    data class TabShortcut(val tabId: Long) : HomeAction
}
