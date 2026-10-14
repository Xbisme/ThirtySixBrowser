package com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** Spec 020 FR-024a — counts one app launch, stopping at the invitation threshold. */
class RecordAppLaunchUseCase @Inject constructor(
    private val repository: DefaultBrowserInviteRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.recordLaunch()
}
