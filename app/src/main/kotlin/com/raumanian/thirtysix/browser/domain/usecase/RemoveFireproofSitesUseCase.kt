package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.FireproofSiteRepository
import javax.inject.Inject

/** Spec 024 FR-012 — removes one fireproof site, or all of them when [site] is `null`. */
class RemoveFireproofSitesUseCase @Inject constructor(
    private val repository: FireproofSiteRepository,
) {
    suspend operator fun invoke(site: String?): Result<Unit> =
        if (site == null) repository.clearAll() else repository.remove(site)
}
