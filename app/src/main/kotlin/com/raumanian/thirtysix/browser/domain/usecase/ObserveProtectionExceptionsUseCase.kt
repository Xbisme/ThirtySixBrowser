package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.domain.repository.ProtectionExceptionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Spec 023 FR-015 — the sites without protection, newest first. */
class ObserveProtectionExceptionsUseCase @Inject constructor(
    private val exceptions: ProtectionExceptionRepository,
) {
    operator fun invoke(): Flow<List<SiteProtectionException>> = exceptions.observeAll()
}
