package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.FireproofSiteRepository
import javax.inject.Inject

/**
 * Spec 024 FR-009 — fireproofs [site] (a registrable domain, as `PageProtectionStatus.site`) or
 * stops fireproofing it. Normal browsing only: the dashboard never offers this in incognito
 * (FR-011), so there is no incognito branch.
 */
class SetSiteFireproofUseCase @Inject constructor(
    private val repository: FireproofSiteRepository,
) {
    suspend operator fun invoke(site: String, fireproof: Boolean): Result<Unit> =
        if (fireproof) repository.add(site.lowercase()) else repository.remove(site.lowercase())
}
