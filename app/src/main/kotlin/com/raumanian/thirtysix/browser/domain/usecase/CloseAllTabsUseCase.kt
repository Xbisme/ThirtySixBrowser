package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 011 — wipe every tab and create exactly one fresh home tab (US3 #4 /
 * FR-012). Atomic at the repository layer (Mutex-guarded — R5/R10).
 *
 * Triggered from the switcher's "Close all tabs" affordance after the user
 * confirms the destructive-action dialog (`CloseAllTabsConfirmDialog`).
 */
class CloseAllTabsUseCase @Inject constructor(
    private val repository: TabRepository,
) {
    suspend operator fun invoke() = repository.closeAllTabs()
}
