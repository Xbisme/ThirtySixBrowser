package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
/**
 * Immutable UI snapshot for the Browser screen. There is no `DEFAULT`: the initial state depends
 * on the injected home URL.
 */
data class BrowserUiState(
    val currentUrl: String,
    val loadingState: LoadingState,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val addressBarText: String = "",
    val isAddressBarFocused: Boolean = false,
    /** The active tab is incognito: drives the indicator, the disk-cache gates and the WebView lockdown. */
    val isIncognito: Boolean = false,
    val isBookmarked: Boolean = false,
    /** Home was pressed on a loaded page and covers it; Back clears it. */
    val homeOverlay: Boolean = false,
    /** This tab left the Home page; Back without page history returns there. */
    val cameFromHome: Boolean = false,
    val isDesktopMode: Boolean = false,
) {
    /** The tab shows the Home page (or, incognito, the incognito page). */
    val isHomeContent: Boolean
        get() = currentUrl == UrlConstants.HOME_PAGE_URL || homeOverlay
}
