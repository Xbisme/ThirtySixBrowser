package com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions

sealed interface AddressSuggestionsAction {
    data class TextChanged(val text: String, val currentPageUrl: String?) : AddressSuggestionsAction

    data class FocusChanged(val focused: Boolean) : AddressSuggestionsAction
}
