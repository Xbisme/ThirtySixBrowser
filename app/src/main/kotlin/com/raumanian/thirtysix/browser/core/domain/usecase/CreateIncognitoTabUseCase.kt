package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Opens a new incognito tab; the session's cookie isolation is handled inside the repository. */
class CreateIncognitoTabUseCase @Inject constructor(
    private val repository: IncognitoTabRepository,
) {
    suspend operator fun invoke(url: String): Result<Tab> = repository.createTab(url)
}
