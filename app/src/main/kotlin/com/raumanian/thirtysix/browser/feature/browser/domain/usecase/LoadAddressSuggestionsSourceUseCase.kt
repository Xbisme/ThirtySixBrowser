package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestionSnapshot
import com.raumanian.thirtysix.browser.feature.browser.domain.model.SuggestionSource
import javax.inject.Inject

/**
 * Spec 022 FR-015 / FR-016 — the candidates for one editing session, read once from what is
 * already on the device and nothing else.
 *
 * Bookmarks come first, newest saved first; then history, newest visit first. An address appears
 * once (I-10): the newest bookmark of a duplicated address wins, and a bookmarked address is left
 * out of the history half (I-11). Combining two repositories here keeps them independent of each
 * other (Constitution §IV).
 */
class LoadAddressSuggestionsSourceUseCase @Inject constructor(
    private val bookmarks: BookmarkRepository,
    private val history: HistoryRepository,
) {
    suspend operator fun invoke(): AddressSuggestionSnapshot {
        val seen = HashSet<String>()
        val candidates = ArrayList<AddressSuggestion>()
        bookmarks.getAllBookmarks().forEach { bookmark ->
            if (seen.add(bookmark.url)) {
                candidates += AddressSuggestion(bookmark.url, bookmark.title, SuggestionSource.Bookmark)
            }
        }
        history.getLatestVisitPerUrl().forEach { entry ->
            if (seen.add(entry.url)) {
                candidates += AddressSuggestion(entry.url, entry.title, SuggestionSource.History)
            }
        }
        return AddressSuggestionSnapshot(candidates)
    }
}
