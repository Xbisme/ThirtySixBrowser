package com.raumanian.thirtysix.browser.presentation.browser.components

/** Spec 019 — what each slot of [BrowserBottomBar] does (FR-020). */
internal data class BrowserBottomBarCallbacks(
    val onHome: () -> Unit,
    val onSearch: () -> Unit,
    val onNewTab: () -> Unit,
    val onTabs: () -> Unit,
    /** Spec 011 Q2 — long-press on Tabs still opens a new tab inline. */
    val onTabsLongPress: () -> Unit,
    val onMenu: () -> Unit,
)
