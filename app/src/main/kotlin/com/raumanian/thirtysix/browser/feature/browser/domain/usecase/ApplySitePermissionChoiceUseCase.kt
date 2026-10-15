package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import javax.inject.Inject

enum class SitePermissionChoice { AlwaysAllow, AllowOnce, Deny }

/**
 * What the caller does next: [allowNow] → request the Android permission if missing, then grant;
 * [grantOnce] → also record a one-time grant for this tab's page.
 */
data class SiteChoiceEffect(val allowNow: Boolean, val grantOnce: Boolean)

/** Records the answer; incognito never writes, so there every allow is a one-time grant. */
class ApplySitePermissionChoiceUseCase @Inject constructor(
    private val repository: SitePermissionRepository,
) {
    suspend operator fun invoke(
        choice: SitePermissionChoice,
        origin: String,
        capabilities: Set<SiteCapability>,
        isIncognito: Boolean,
    ): SiteChoiceEffect = when {
        isIncognito -> {
            val allow = choice != SitePermissionChoice.Deny
            SiteChoiceEffect(allowNow = allow, grantOnce = allow)
        }
        choice == SitePermissionChoice.AlwaysAllow -> {
            capabilities.forEach { repository.save(origin, it, SiteDecision.Allow) }
            SiteChoiceEffect(allowNow = true, grantOnce = false)
        }
        choice == SitePermissionChoice.AllowOnce -> SiteChoiceEffect(allowNow = true, grantOnce = true)
        else -> {
            capabilities.forEach { repository.save(origin, it, SiteDecision.Deny) }
            SiteChoiceEffect(allowNow = false, grantOnce = false)
        }
    }
}
