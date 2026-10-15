package com.raumanian.thirtysix.browser.feature.browser.presentation.home

sealed interface DefaultBrowserInviteAction {
    data object Resumed : DefaultBrowserInviteAction

    data object Dismiss : DefaultBrowserInviteAction
}
