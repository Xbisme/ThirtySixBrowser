package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Spec 020 contracts C3.3 — the Home invitation's on-device state. A narrow repository of its
 * own rather than new `SettingsRepository` members, so the existing settings fakes stay untouched
 * and app state stays out of the user-settings model (research R9).
 */
interface DefaultBrowserInviteRepository {
    fun observeInviteState(): Flow<InviteState>

    /** Retires the invitation for good; never reverted (data-model INV-8). */
    suspend fun markInviteDismissed(): Result<Unit>

    /** Counts one app launch, stopping at the invitation threshold (FR-024a). */
    suspend fun recordLaunch(): Result<Unit>
}

/** Spec 020 data-model §8. */
data class InviteState(val dismissed: Boolean, val launchCount: Int) {
    companion object {
        val DEFAULT = InviteState(dismissed = false, launchCount = 0)
    }
}
