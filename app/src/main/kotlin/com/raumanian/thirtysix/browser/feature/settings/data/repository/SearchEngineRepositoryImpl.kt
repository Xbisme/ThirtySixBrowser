package com.raumanian.thirtysix.browser.feature.settings.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Reads the engine on every submit, so a change made while typing applies. Depending on
 * [SettingsRepository] is an accepted exception: settings are the app-wide configuration backbone.
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
