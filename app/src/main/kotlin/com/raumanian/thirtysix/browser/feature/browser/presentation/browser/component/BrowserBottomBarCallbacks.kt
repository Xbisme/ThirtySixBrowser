package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

internal data class BrowserBottomBarCallbacks(
    val onHome: () -> Unit,
    val onSearch: () -> Unit,
    val onNewTab: () -> Unit,
    val onTabs: () -> Unit,
    /** Long-press on Tabs opens a new tab directly. */
    val onTabsLongPress: () -> Unit,
    val onMenu: () -> Unit,
)
