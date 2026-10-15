package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

data class AddressBarUiState(
    val text: String = "",
    val isFocused: Boolean = false,
    /** Home was pressed on a loaded page and covers it; Back clears it. */
    val homeOverlay: Boolean = false,
    /** This tab left the Home page; Back without page history returns there. */
    val cameFromHome: Boolean = false,
)
