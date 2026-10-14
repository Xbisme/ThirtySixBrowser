package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** Home's default-browser invitation state, kept apart from the user-settings model. */
interface DefaultBrowserInviteRepository {
    fun observeInviteState(): Flow<InviteState>

    /** Retires the invitation for good. */
    suspend fun markInviteDismissed(): Result<Unit>

    /** Counts one app launch, stopping at the invitation threshold. */
    suspend fun recordLaunch(): Result<Unit>
}

data class InviteState(val dismissed: Boolean, val launchCount: Int) {
    companion object {
        val DEFAULT = InviteState(dismissed = false, launchCount = 0)
    }
}
