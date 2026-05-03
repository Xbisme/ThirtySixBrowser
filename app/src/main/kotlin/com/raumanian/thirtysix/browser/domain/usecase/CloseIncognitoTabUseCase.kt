package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import javax.inject.Inject

/**
 * Spec 012 — close a single incognito tab.
 *
 * Constitution §IV documented exception (see plan.md Complexity Tracking):
 * the close-of-last-incognito-tab transaction coordinates the in-memory tab
 * removal AND the cookie-jar snapshot restore as a single user-perceived
 * operation. Both side effects live inside [IncognitoTabRepository.closeTab]
 * (the repository serializes them under its own mutex), so the use case stays
 * thin — but the conceptual coordination is what the §IV exception covers.
 */
class CloseIncognitoTabUseCase @Inject constructor(
    private val repository: IncognitoTabRepository,
) {
    suspend operator fun invoke(tabId: Long) = repository.closeTab(tabId)
}
