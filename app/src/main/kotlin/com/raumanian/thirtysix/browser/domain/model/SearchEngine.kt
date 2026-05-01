// Spec 006 — Search engine choice. v1.0 ships only Google; the type is shaped
// as an enumeration so future additions don't require changing the persistence
// shape (Spec 010 / Spec 016 will add picker UI + more entries).

package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults

enum class SearchEngine(val storageValue: String) {
    Google("google"),
    ;

    companion object {
        /**
         * Map an on-disk storage value back to a SearchEngine variant.
         *
         * Falls back to [AppDefaults.SEARCH_ENGINE] when:
         *  - value is null (key absent on disk → first-launch default)
         *  - value does not match any known variant (FR-009 unknown-enum fallback,
         *    e.g., user downgraded from a future build that wrote `"bing"`)
         */
        fun fromStorageValueOrDefault(value: String?): SearchEngine =
            entries.firstOrNull { it.storageValue == value } ?: AppDefaults.SEARCH_ENGINE
    }
}
