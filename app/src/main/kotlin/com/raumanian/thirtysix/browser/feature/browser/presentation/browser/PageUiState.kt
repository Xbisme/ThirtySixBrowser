package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** What the page holder owns; the URL and incognito flag it shares live in `CurrentPageRepository`. */
data class PageUiState(
    val loadingState: LoadingState = LoadingState.Idle,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val addressBarText: String = "",
    val isAddressBarFocused: Boolean = false,
    /** Home was pressed on a loaded page and covers it; Back clears it. */
    val homeOverlay: Boolean = false,
    /** This tab left the Home page; Back without page history returns there. */
    val cameFromHome: Boolean = false,
)
