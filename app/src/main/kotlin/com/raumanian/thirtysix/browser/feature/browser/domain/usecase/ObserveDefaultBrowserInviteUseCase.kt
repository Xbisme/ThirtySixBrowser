package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.InviteState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The invitation's dismissed flag and launch count. */
class ObserveDefaultBrowserInviteUseCase @Inject constructor(
    private val repository: DefaultBrowserInviteRepository,
) {
    operator fun invoke(): Flow<InviteState> = repository.observeInviteState()
}
