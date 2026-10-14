package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import javax.inject.Inject

/**
 * Spec 023 FR-013, FR-014 — whether protections apply to [site] (a registrable domain).
 * Synchronous and safe on any thread, including WebView IO threads.
 *
 * Normal tabs read the remembered list; incognito tabs read only the incognito list, never the
 * normal one. This is the only way presentation code learns a site's protection state (§IV).
 */
class IsSiteProtectedUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
    private val incognitoExceptions: IncognitoProtectionExceptions,
) {
    operator fun invoke(site: String, isIncognito: Boolean): Boolean =
        if (isIncognito) !incognitoExceptions.contains(site) else site !in exceptions.currentSites()
}
