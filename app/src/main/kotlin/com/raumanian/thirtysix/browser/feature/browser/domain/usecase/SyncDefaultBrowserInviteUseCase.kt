package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import javax.inject.Inject

/**
 * Runs on every foreground: once the app is the default browser, by any route, the invitation is
 * retired for good, so it does not return if another browser later becomes the default.
 */
class SyncDefaultBrowserInviteUseCase @Inject constructor(
    private val controller: DefaultBrowserController,
    private val repository: DefaultBrowserInviteRepository,
) {
    suspend operator fun invoke() {
        if (controller.isDefaultBrowser()) repository.markInviteDismissed()
    }
}
