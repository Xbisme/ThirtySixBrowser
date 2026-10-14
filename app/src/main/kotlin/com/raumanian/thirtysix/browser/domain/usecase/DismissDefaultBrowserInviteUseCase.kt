package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.domain.repository.DefaultBrowserInviteRepository
import javax.inject.Inject

/** Spec 020 FR-025 — the person closed the invitation; it never returns on its own. */
class DismissDefaultBrowserInviteUseCase @Inject constructor(
    private val repository: DefaultBrowserInviteRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.markInviteDismissed()
}
