package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Immutable

/** Find in page for the active tab. [current] is 1-based and 0 without a match; `current ≤ total`. */
@Immutable
data class FindInPageState(
    val isOpen: Boolean = false,
    val query: String = "",
    val current: Int = 0,
    val total: Int = 0,
) {
    val counterVisible: Boolean get() = query.isNotEmpty()

    val canStep: Boolean get() = total > 0
}
