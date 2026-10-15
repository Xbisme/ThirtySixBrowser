package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/**
 * Fireproofs [site] (a registrable domain) or stops fireproofing it. Normal browsing only: the
 * dashboard never offers this in incognito.
 */
class SetSiteFireproofUseCase @Inject constructor(
    private val repository: FireproofSiteRepository,
) {
    suspend operator fun invoke(site: String, fireproof: Boolean): Result<Unit> =
        if (fireproof) repository.add(site.lowercase()) else repository.remove(site.lowercase())
}
