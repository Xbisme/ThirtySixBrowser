package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Spec 023 FR-015 — Settings removes one site without protection, or all when [site] is null. */
class RemoveProtectionExceptionsUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
) {
    suspend operator fun invoke(site: String?): Result<Unit> =
        if (site == null) exceptions.clearAll() else exceptions.remove(site)
}
