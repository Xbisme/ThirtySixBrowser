package com.raumanian.thirtysix.browser.feature.sitepermission.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSitePermissionsUseCase @Inject constructor(
    private val repository: SitePermissionRepository,
) {
    operator fun invoke(): Flow<List<SitePermissionDecision>> = repository.observeAll()
}
