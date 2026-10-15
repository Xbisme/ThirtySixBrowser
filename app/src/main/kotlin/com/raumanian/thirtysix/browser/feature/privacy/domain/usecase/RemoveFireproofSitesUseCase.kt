package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Removes one fireproof site, or all of them when [site] is `null`. */
class RemoveFireproofSitesUseCase @Inject constructor(
    private val repository: FireproofSiteRepository,
) {
    suspend operator fun invoke(site: String?): Result<Unit> =
        if (site == null) repository.clearAll() else repository.remove(site)
}
