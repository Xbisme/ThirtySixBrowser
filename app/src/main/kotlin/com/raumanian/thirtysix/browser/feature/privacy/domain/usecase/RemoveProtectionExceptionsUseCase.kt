package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Removes one site without protection, or all when [site] is `null`. */
class RemoveProtectionExceptionsUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
) {
    suspend operator fun invoke(site: String?): Result<Unit> =
        if (site == null) exceptions.clearAll() else exceptions.remove(site)
}
