@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.runtime.Composable
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionViewModel
/** The privacy dashboard and the Fire confirmation and overlay, hosted over the browser. */
@Composable
@Suppress("LongParameterList")
internal fun PrivacyToolsHosts(
    dashboardOpen: Boolean,
    isHomeContent: Boolean,
    onDashboardClose: () -> Unit,
    protection: ProtectionViewModel,
) {
    // No dashboard on Home or the incognito start page.
    if (dashboardOpen && !isHomeContent) {
        PrivacyDashboardSheet(
            dashboardFlow = protection.dashboard,
            onDismiss = onDashboardClose,
            onProtectionSwitch = { on ->
                // Exactly like the hub switch: close, then reload with the new setting.
                onDashboardClose()
                protection.onProtectionSwitched(on)
            },
            onFireproofSwitch = protection::onFireproofSwitched,
        )
    }
}
