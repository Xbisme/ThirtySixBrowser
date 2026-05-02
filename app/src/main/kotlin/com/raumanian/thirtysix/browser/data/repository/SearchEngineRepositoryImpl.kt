package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Spec 010 — concrete [SearchEngineRepository].
 *
 * Reads the user's currently-persisted [SearchEngine] from [SettingsRepository]
 * once per call via `observeSettings().first()` (read-on-submit semantics; see
 * spec.md edge case "Engine preference changes mid-typing"), URL-encodes the
 * query as UTF-8, and substitutes it into the engine's documented template
 * from [UrlConstants].
 *
 * The Repository → Repository dependency on [SettingsRepository] is documented
 * as an accepted exception in the Spec 010 Complexity Tracking table (settings
 * is a project-wide configuration backbone, mirrors Spec 006's
 * `ObserveUserSettingsUseCase` pattern of reading from [SettingsRepository]
 * via the same flow).
 *
 * Encoding form (`URLEncoder.encode(query, "UTF-8")` — Java legacy form-encoder
 * that emits `+` for space) is intentionally identical to Spec 009's inline
 * implementation so SC-001's byte-identity contract holds for the Google
 * engine path.
 */
class SearchEngineRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : SearchEngineRepository {

    override suspend fun buildSearchUrl(query: String): String {
        val engine = settingsRepository.observeSettings().first().searchEngine
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val template = when (engine) {
            SearchEngine.Google -> UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE
            SearchEngine.DuckDuckGo -> UrlConstants.DUCKDUCKGO_SEARCH_URL_TEMPLATE
            SearchEngine.Bing -> UrlConstants.BING_SEARCH_URL_TEMPLATE
        }
        return String.format(template, encoded)
    }
}
