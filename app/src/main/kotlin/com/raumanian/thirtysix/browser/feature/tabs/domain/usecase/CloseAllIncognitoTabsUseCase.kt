package com.raumanian.thirtysix.browser.feature.tabs.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import javax.inject.Inject

/** Closes every incognito tab at once, ending the incognito session exactly once. */
class CloseAllIncognitoTabsUseCase @Inject constructor(
    private val repository: IncognitoTabRepository,
) {
    suspend operator fun invoke() = repository.closeAll()
}
