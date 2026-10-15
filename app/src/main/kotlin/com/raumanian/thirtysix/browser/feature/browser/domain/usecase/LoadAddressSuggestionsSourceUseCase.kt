package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestionSnapshot
import com.raumanian.thirtysix.browser.feature.browser.domain.model.SuggestionSource
import javax.inject.Inject

/**
 * The candidates for one editing session, read once from on-device data only: bookmarks, newest
 * first, then history, newest first. An address appears once: the newest bookmark of a duplicate
 * wins, and a bookmarked address is left out of the history half.
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
