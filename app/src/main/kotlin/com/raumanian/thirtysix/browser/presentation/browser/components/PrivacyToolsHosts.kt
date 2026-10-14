@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.runtime.Composable
import com.raumanian.thirtysix.browser.domain.model.FireState
import com.raumanian.thirtysix.browser.presentation.browser.fire.FireViewModel
import com.raumanian.thirtysix.browser.presentation.browser.protection.ProtectionViewModel

/**
 * Spec 024 — the privacy dashboard (US2, US3) and the Fire confirmation and overlay (US1), hosted
 * over the browser.
 */
@Composable
@Suppress("LongParameterList")
internal fun PrivacyToolsHosts(
    fire: FireViewModel,
    fireState: FireState,
    fireConfirmOpen: Boolean,
    onFireConfirmClosed: () -> Unit,
    dashboardOpen: Boolean,
    isHomeContent: Boolean,
    onDashboardClosed: () -> Unit,
    protection: ProtectionViewModel,
) {
    // FR-015 — no dashboard on Home or the incognito start page.
    if (dashboardOpen && !isHomeContent) {
        PrivacyDashboardSheet(
            dashboardFlow = protection.dashboard,
            onDismiss = onDashboardClosed,
            onProtectionSwitched = { on ->
                // FR-017 — exactly the hub switch: close, then reload with the new setting.
                onDashboardClosed()
                protection.onProtectionSwitched(on)
            },
            onFireproofSwitched = protection::onFireproofSwitched,
        )
    }
    if (fireConfirmOpen) {
        FireConfirmDialog(
            onConfirm = {
                onFireConfirmClosed()
                fire.fire()
            },
            onDismiss = onFireConfirmClosed,
        )
    }
    FireOverlay(state = fireState, onFinished = fire::acknowledge)
}
