package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.feature.browser.domain.model.CurrentPage

/** The browser screen's snapshot, assembled at the route; see [browserUiState]. */
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
    val homeOverlay: Boolean = false,
    val cameFromHome: Boolean = false,
    val isDesktopMode: Boolean = false,
) {
    /** The tab shows the Home page (or, incognito, the incognito page). */
    val isHomeContent: Boolean
        get() = currentUrl == UrlConstants.HOME_PAGE_URL || homeOverlay
}

fun browserUiState(
    page: CurrentPage,
    own: PageUiState,
    addressBar: AddressBarUiState,
    actions: PageActionsUiState,
): BrowserUiState = BrowserUiState(
    currentUrl = page.url,
    loadingState = own.loadingState,
    canGoBack = own.canGoBack,
    canGoForward = own.canGoForward,
    addressBarText = addressBar.text,
    isAddressBarFocused = addressBar.isFocused,
    isIncognito = page.isIncognito,
    isBookmarked = actions.isBookmarked,
    homeOverlay = addressBar.homeOverlay,
    cameFromHome = addressBar.cameFromHome,
    isDesktopMode = actions.isDesktopMode,
)
