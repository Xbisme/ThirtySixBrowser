package com.raumanian.thirtysix.browser.feature.browser.domain.model

/** Spec 022 data-model §4 — where a suggestion comes from. */
enum class SuggestionSource { Bookmark, History }

/**
 * Spec 022 data-model §4 — one address suggestion while typing. [url] is its identity: no address
 * appears twice in a list (I-10). An empty [title] is shown as the hostname (FR-018).
 */
data class AddressSuggestion(
    val url: String,
    val title: String,
    val source: SuggestionSource,
)

/**
 * Spec 022 data-model §4 — every candidate for one editing session, in display order: bookmarks
 * first, newest saved first, then history, newest visit first, without the addresses already
 * bookmarked (I-11). Built once when editing starts and dropped when it ends.
 */
data class AddressSuggestionSnapshot(val candidates: List<AddressSuggestion>) {
    companion object {
        val EMPTY: AddressSuggestionSnapshot = AddressSuggestionSnapshot(emptyList())
    }
}
