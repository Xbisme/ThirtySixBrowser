// Spec 006 — Search engine choice. The type is shaped as an enumeration so
// future additions don't require changing the persistence shape.
// Spec 010 (`search-engine-google`) adds DuckDuckGo + Bing alongside Google
// (default). Each entry retains a stable `storageValue` so the existing
// settings persistence schema continues to round-trip without migration —
// `fromStorageValueOrDefault(...)` already absorbs unknown values into the
// default. Spec 016 will surface the picker UI; this enum already covers all
// 3 entries it needs.

package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults

enum class SearchEngine(val storageValue: String) {
    Google("google"),
    DuckDuckGo("duckduckgo"),
    Bing("bing"),
    ;

    companion object {
        /**
         * Map an on-disk storage value back to a SearchEngine variant.
         *
         * Falls back to [AppDefaults.SEARCH_ENGINE] when:
         *  - value is null (key absent on disk → first-launch default)
         *  - value does not match any known variant (FR-009 unknown-enum fallback,
         *    e.g., user downgraded from a future build that wrote `"yandex"`)
         */
        fun fromStorageValueOrDefault(value: String?): SearchEngine =
            entries.firstOrNull { it.storageValue == value } ?: AppDefaults.SEARCH_ENGINE
    }
}
