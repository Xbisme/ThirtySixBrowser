package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import javax.inject.Inject

/**
 * Whether protections apply to [site] (a registrable domain); safe on any thread, including
 * WebView IO threads. Incognito tabs read only the incognito list, never the remembered one.
 */
class IsSiteProtectedUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
    private val incognitoExceptions: IncognitoProtectionExceptions,
) {
    operator fun invoke(site: String, isIncognito: Boolean): Boolean =
        if (isIncognito) !incognitoExceptions.contains(site) else site !in exceptions.currentSites()
}
