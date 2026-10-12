package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.domain.model.AddressSuggestionSnapshot
import javax.inject.Inject

/**
 * Spec 022 FR-014 / FR-017 — the suggestions for what has been typed so far.
 *
 * A candidate matches when its title or address contains the text, ignoring case. `contains`
 * compares characters literally (`%`, `_`, `'` and `\` match only themselves) and folds case for
 * every script, which SQLite's ASCII-only `LOWER()` would not (research R5). The snapshot's order
 * is kept, and the scan stops at [limit].
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
