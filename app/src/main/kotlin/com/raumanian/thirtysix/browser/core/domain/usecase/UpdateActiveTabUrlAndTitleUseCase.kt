package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Writes the active page's address and title to its tab; incognito tabs go to the in-memory
 * repository so they are never persisted.
 */
class UpdateActiveTabUrlAndTitleUseCase @Inject constructor(
    private val repository: TabRepository,
    private val incognitoRepository: IncognitoTabRepository,
) {
    suspend operator fun invoke(
        tabId: Long,
        url: String,
        title: String,
        isIncognito: Boolean = false,
    ) {
        if (isIncognito) {
            incognitoRepository.updateTabUrlAndTitle(tabId, url, title)
        } else {
            repository.updateTabUrlAndTitle(tabId, url, title)
        }
    }
}
