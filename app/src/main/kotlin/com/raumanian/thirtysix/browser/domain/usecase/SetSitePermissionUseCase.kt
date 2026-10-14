package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.domain.repository.SitePermissionRepository
import javax.inject.Inject

/** Spec 021 FR-009 — changes one remembered decision from the Site permissions screen. */
class SetSitePermissionUseCase @Inject constructor(
    private val repository: SitePermissionRepository,
) {
    suspend operator fun invoke(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit> =
        repository.save(origin, capability, decision)
}
