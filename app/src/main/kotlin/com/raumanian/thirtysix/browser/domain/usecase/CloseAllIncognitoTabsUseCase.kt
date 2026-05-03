package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import javax.inject.Inject

/**
 * Spec 012 — close every incognito tab in one atomic operation.
 *
 * Triggered by the "Close all incognito" affordance in the tab switcher
 * (US5). The repository's [IncognitoTabRepository.closeAll] wipes the
 * in-memory state and restores the cookie jar snapshot exactly once.
 */
class CloseAllIncognitoTabsUseCase @Inject constructor(
    private val repository: IncognitoTabRepository,
) {
    suspend operator fun invoke() = repository.closeAll()
}
