package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface PageActionsAction {
    data object StarTapped : PageActionsAction

    data object ToggleDesktopMode : PageActionsAction

    data object LongPressNewTab : PageActionsAction
}
