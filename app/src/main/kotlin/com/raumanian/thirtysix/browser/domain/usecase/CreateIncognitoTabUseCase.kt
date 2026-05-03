package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import javax.inject.Inject

/**
 * Spec 012 — open a new incognito tab.
 *
 * Sibling to [CreateTabUseCase] (Spec 011, normal tabs). The cookie-snapshot
 * lifecycle (FR-011a) is hidden inside [IncognitoTabRepository.createTab];
 * callers see only the [Tab] result.
 */
class CreateIncognitoTabUseCase @Inject constructor(
    private val repository: IncognitoTabRepository,
) {
    suspend operator fun invoke(url: String): Result<Tab> = repository.createTab(url)
}
