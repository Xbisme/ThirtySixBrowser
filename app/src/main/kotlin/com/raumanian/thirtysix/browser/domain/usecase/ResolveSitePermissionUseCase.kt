package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.domain.repository.SitePermissionRepository
import javax.inject.Inject

/** Spec 021 contracts C1.1 — what to do with a site's request. */
sealed interface SitePermissionOutcome {
    data object Grant : SitePermissionOutcome

    data object Deny : SitePermissionOutcome

    /** Ask the person about these capabilities (the others are already allowed). */
    data class Ask(val capabilities: Set<SiteCapability>) : SitePermissionOutcome
}

/**
 * Spec 021 research R2 / contracts C1.1 — decides a camera / microphone / location request.
 *
 *  1. Not a secure page, or a capability outside the three → refused, no question (FR-007, FR-008).
 *  2. Incognito → remembered decisions are ignored; only this page's one-time grants count (FR-004).
 *  3. Normal → per capability: remembered allow or one-time grant → granted; remembered deny → the
 *     whole request is refused (one answer for a combined request, FR-005); else it is asked.
 */
class ResolveSitePermissionUseCase @Inject constructor(
    private val repository: SitePermissionRepository,
) {
    /** [grantedOnce] answers whether this tab's current page holds a one-time grant. */
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
