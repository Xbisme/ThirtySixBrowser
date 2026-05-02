package com.raumanian.thirtysix.browser.domain.repository

/**
 * Translates a free-form search query into a fully-formed search-results URL
 * for the user's currently-selected search engine (Spec 010).
 *
 * Engine selection is read at submit-time from [SettingsRepository] (Spec 006)
 * via the existing `observeSettings()` flow + `.first()`. There is no internal
 * cache; every call re-reads the snapshot. See spec.md edge case "Engine
 * preference changes mid-typing" for the read-on-submit semantics rationale.
 *
 * Per Constitution §IV, this interface lives in `domain/repository/` and uses
 * only Kotlin / project domain types. The implementation lives in
 * `data/repository/SearchEngineRepositoryImpl.kt`.
 */
interface SearchEngineRepository {

    /**
     * Build a fully-formed search-results URL for [query] under the user's
     * currently-persisted [com.raumanian.thirtysix.browser.domain.model.SearchEngine].
     *
     * @param query the trimmed, non-empty search query as typed by the user.
     *              The caller (`BuildSearchUrlUseCase` via `BrowserViewModel`)
     *              is responsible for ensuring non-emptiness — Spec 009's
     *              `AddressBarInputClassifier` returns `Empty` before reaching
     *              the Query branch, so this method is never called with an
     *              empty input under normal operation.
     * @return a fully-formed absolute HTTPS URL with the URL-encoded query
     *         substituted into the engine's documented template. Always starts
     *         with `https://`.
     */
    suspend fun buildSearchUrl(query: String): String
}
