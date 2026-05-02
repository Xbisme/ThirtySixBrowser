package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import javax.inject.Inject

/**
 * Build the search-results URL for a free-form query under the user's
 * currently-selected search engine (Spec 010).
 *
 * Operator-invocable so call sites read as `buildSearchUrl(query)` rather
 * than `buildSearchUrl.invoke(query)`. Pure delegation to repository — exists
 * per Constitution §IV "all business logic in domain/usecase/" so the
 * consumer-facing API is stable as Spec 016 (settings screen) potentially
 * grows it.
 *
 * Mirrors the pattern established by Spec 006's `ObserveUserSettingsUseCase`.
 */
class BuildSearchUrlUseCase @Inject constructor(
    private val repository: SearchEngineRepository,
) {
    suspend operator fun invoke(query: String): String = repository.buildSearchUrl(query)
}
