package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** The person closed the invitation; it never returns on its own. */
class DismissDefaultBrowserInviteUseCase @Inject constructor(
    private val repository: DefaultBrowserInviteRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.markInviteDismissed()
}
