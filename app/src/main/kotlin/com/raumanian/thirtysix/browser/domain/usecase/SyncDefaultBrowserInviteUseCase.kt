package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import javax.inject.Inject

/**
 * Spec 020 FR-026 / research R9 — run on every foreground: once the app sees itself as the default
 * browser, by whatever route it got there (the invitation, the Settings row or system settings),
 * the invitation is retired for good, so it cannot come back if another browser later becomes
 * the default. The invitation's own action never writes this (FR-025, FR-027).
 */
class SyncDefaultBrowserInviteUseCase @Inject constructor(
    private val controller: DefaultBrowserController,
    private val repository: DefaultBrowserInviteRepository,
) {
    suspend operator fun invoke() {
        if (controller.isDefaultBrowser()) repository.markInviteDismissed()
    }
}
