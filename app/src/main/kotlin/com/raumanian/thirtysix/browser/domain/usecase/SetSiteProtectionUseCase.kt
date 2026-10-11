package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.domain.repository.ProtectionExceptionRepository
import javax.inject.Inject

/**
 * Spec 023 FR-012 – FR-014 — turns protection on or off for [site]. A normal tab's choice is
 * saved; an incognito tab's choice lives only in memory and never touches the saved list.
 */
class SetSiteProtectionUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
    private val incognitoExceptions: IncognitoProtectionExceptions,
) {
    suspend operator fun invoke(site: String, on: Boolean, isIncognito: Boolean): Result<Unit> {
        if (isIncognito) {
            if (on) incognitoExceptions.remove(site) else incognitoExceptions.add(site)
            return Result.Success(Unit)
        }
        return if (on) exceptions.remove(site) else exceptions.add(site)
    }
}
