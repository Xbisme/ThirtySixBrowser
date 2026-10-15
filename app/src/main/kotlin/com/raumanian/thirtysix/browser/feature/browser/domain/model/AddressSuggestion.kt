package com.raumanian.thirtysix.browser.feature.browser.domain.model

/** Where a suggestion comes from. */
enum class SuggestionSource { Bookmark, History }

/**
 * One address suggestion while typing. [url] is its identity: no address appears twice in a list.
 * An empty [title] is shown as the hostname.
 */
data class AddressSuggestion(
    val url: String,
    val title: String,
    val source: SuggestionSource,
)

/**
 * Every candidate for one editing session, in display order: bookmarks, newest saved first, then
 * history, newest visit first, without addresses already bookmarked. Built once when editing
 * starts and dropped when it ends.
 */
data class AddressSuggestionSnapshot(val candidates: List<AddressSuggestion>) {
    companion object {
        val EMPTY: AddressSuggestionSnapshot = AddressSuggestionSnapshot(emptyList())
    }
}
