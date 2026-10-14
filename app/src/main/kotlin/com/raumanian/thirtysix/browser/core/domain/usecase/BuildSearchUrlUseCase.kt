package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import javax.inject.Inject

/** The search-results URL for [query] under the currently selected search engine. */
class BuildSearchUrlUseCase @Inject constructor(
    private val repository: SearchEngineRepository,
) {
    suspend operator fun invoke(query: String): String = repository.buildSearchUrl(query)
}
