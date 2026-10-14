package com.raumanian.thirtysix.browser.feature.sitepermission.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Spec 021 FR-009 — forgets one site's decisions ([origin]) or, with `null`, every site's. */
class RemoveSitePermissionsUseCase @Inject constructor(
    private val repository: SitePermissionRepository,
) {
    suspend operator fun invoke(origin: String?): Result<Unit> =
        if (origin == null) repository.clearAll() else repository.removeSite(origin)
}
