package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestionSnapshot
import javax.inject.Inject

/**
 * Suggestions whose title or address contains the typed text, ignoring case. `contains` is literal
 * (`%`, `_`, `'`, `\` match only themselves) and folds case for every script, unlike SQLite's
 * ASCII-only `LOWER()`. The snapshot's order is kept, and the scan stops at [limit].
 */
class FilterAddressSuggestionsUseCase @Inject constructor() {
    operator fun invoke(snapshot: AddressSuggestionSnapshot, query: String, limit: Int): List<AddressSuggestion> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val matches = ArrayList<AddressSuggestion>(limit)
        for (candidate in snapshot.candidates) {
            if (candidate.matches(query)) {
                matches += candidate
                if (matches.size == limit) break
            }
        }
        return matches
    }

    private fun AddressSuggestion.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) || url.contains(query, ignoreCase = true)
}
