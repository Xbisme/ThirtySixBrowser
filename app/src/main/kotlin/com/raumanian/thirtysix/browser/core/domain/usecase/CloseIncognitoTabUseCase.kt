package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import javax.inject.Inject

/**
 * Closes one incognito tab. Closing the last one also ends the session's cookie isolation;
 * [IncognitoTabRepository.closeTab] does both under its own mutex.
 */
class CloseIncognitoTabUseCase @Inject constructor(
    private val repository: IncognitoTabRepository,
) {
    suspend operator fun invoke(tabId: Long) = repository.closeTab(tabId)
}
