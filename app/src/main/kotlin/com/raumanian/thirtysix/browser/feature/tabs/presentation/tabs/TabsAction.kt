package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

sealed interface TabsAction {
    data class TabClick(val tabId: Long) : TabsAction

    data class CloseTab(val tabId: Long) : TabsAction

    data object NewTabClick : TabsAction

    data object NewIncognitoTabClick : TabsAction

    data object CloseAllRequested : TabsAction

    data object CloseAllDismissed : TabsAction

    data object CloseAllConfirmed : TabsAction

    data object CloseAllIncognitoRequested : TabsAction

    data object CloseAllIncognitoDismissed : TabsAction

    data object CloseAllIncognitoConfirmed : TabsAction

    data class ModeSelected(val mode: TabsMode) : TabsAction
}
