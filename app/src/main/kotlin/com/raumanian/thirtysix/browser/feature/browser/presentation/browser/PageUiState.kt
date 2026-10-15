package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** What the page holder owns; the URL and incognito flag it shares live in `CurrentPageRepository`. */
data class PageUiState(
    val loadingState: LoadingState = LoadingState.Idle,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)
