package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.ProtectionExceptionRepository
import javax.inject.Inject

/** Spec 023 FR-015 — Settings removes one site without protection, or all when [site] is null. */
class RemoveProtectionExceptionsUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
) {
    suspend operator fun invoke(site: String?): Result<Unit> =
        if (site == null) exceptions.clearAll() else exceptions.remove(site)
}
