package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The fireproof sites, newest first. */
class ObserveFireproofSitesUseCase @Inject constructor(
    private val repository: FireproofSiteRepository,
) {
    operator fun invoke(): Flow<List<FireproofSite>> = repository.observeAll()
}
