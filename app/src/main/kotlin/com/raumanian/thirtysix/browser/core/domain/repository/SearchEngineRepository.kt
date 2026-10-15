package com.raumanian.thirtysix.browser.core.domain.repository

/** Reads the selected engine on every call, so a change made mid-typing applies to the next submit. */
interface SearchEngineRepository {

    /**
     * [query] is trimmed and non-empty; the result is an absolute `https://` URL with the query
     * URL-encoded into the engine's template.
     */
    suspend fun buildSearchUrl(query: String): String
}
