package com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision

sealed interface SitePermissionsAction {
    data class DecisionClick(
        val origin: String,
        val capability: SiteCapability,
        val current: SiteDecision,
    ) : SitePermissionsAction

    data class DecisionChosen(val decision: SiteDecision) : SitePermissionsAction

    data object EditDismissed : SitePermissionsAction

    data class RemoveSite(val origin: String) : SitePermissionsAction

    data object RemoveAllClick : SitePermissionsAction

    data object RemoveAllConfirmed : SitePermissionsAction

    data object RemoveAllDismissed : SitePermissionsAction
}
