package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
enum class SearchEngine(val storageValue: String) {
    Google("google"),
    DuckDuckGo("duckduckgo"),
    Bing("bing"),
    ;

    companion object {
        // An unknown value (e.g. written by a newer build) falls back to the default.
        fun fromStorageValueOrDefault(value: String?): SearchEngine =
            entries.firstOrNull { it.storageValue == value } ?: AppDefaults.SEARCH_ENGINE
    }
}
