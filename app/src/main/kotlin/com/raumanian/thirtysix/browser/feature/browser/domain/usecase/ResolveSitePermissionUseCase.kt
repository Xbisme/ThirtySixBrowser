package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import javax.inject.Inject

sealed interface SitePermissionOutcome {
    data object Grant : SitePermissionOutcome

    data object Deny : SitePermissionOutcome

    /** Ask the person about these capabilities (the others are already allowed). */
    data class Ask(val capabilities: Set<SiteCapability>) : SitePermissionOutcome
}

/**
 * Decides a camera / microphone / location request. Incognito counts only this page's one-time
 * grants ([grantedOnce]); a remembered deny refuses the whole request.
 */
class ResolveSitePermissionUseCase @Inject constructor(
    private val repository: SitePermissionRepository,
) {
    suspend operator fun invoke(
        origin: String,
        capabilities: Set<SiteCapability>,
        isIncognito: Boolean,
        hasUnsupported: Boolean = false,
        grantedOnce: (SiteCapability) -> Boolean = { false },
    ): SitePermissionOutcome {
        if (!SiteOrigin.isSecure(origin) || hasUnsupported || capabilities.isEmpty()) {
            return SitePermissionOutcome.Deny
        }
        val remembered = if (isIncognito) emptyMap() else repository.decisionsFor(origin)
        val undecided = capabilities.filterNot { remembered[it] == SiteDecision.Allow || grantedOnce(it) }.toSet()
        return when {
            capabilities.any { remembered[it] == SiteDecision.Deny } -> SitePermissionOutcome.Deny
            undecided.isEmpty() -> SitePermissionOutcome.Grant
            else -> SitePermissionOutcome.Ask(undecided)
        }
    }
}
