package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Spec 021 FR-009 — every remembered site decision, for the Site permissions screen. */
class ObserveSitePermissionsUseCase @Inject constructor(
    private val repository: SitePermissionRepository,
) {
    operator fun invoke(): Flow<List<SitePermissionDecision>> = repository.observeAll()
}
