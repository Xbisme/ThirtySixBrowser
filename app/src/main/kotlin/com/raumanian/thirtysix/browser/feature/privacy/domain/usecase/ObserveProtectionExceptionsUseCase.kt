package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Spec 023 FR-015 — the sites without protection, newest first. */
class ObserveProtectionExceptionsUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
) {
    operator fun invoke(): Flow<List<SiteProtectionException>> = exceptions.observeAll()
}
