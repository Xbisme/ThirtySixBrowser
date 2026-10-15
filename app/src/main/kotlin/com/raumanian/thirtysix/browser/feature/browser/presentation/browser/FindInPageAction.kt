package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface FindInPageAction {
    data object Opened : FindInPageAction

    data class QueryChanged(val query: String) : FindInPageAction

    data object Closed : FindInPageAction

    /** What the page's WebView reports for the current query. */
    data class Result(val activeOrdinal: Int, val total: Int) : FindInPageAction
}
