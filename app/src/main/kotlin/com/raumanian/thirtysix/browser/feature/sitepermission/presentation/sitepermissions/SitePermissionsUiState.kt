package com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
/** Decisions in the order camera, microphone, location. */
data class SitePermissionsSite(
    val origin: String,
    val decisions: List<Pair<SiteCapability, SiteDecision>>,
)

/** The decision being changed in the choice dialog. */
data class SitePermissionEdit(val origin: String, val capability: SiteCapability, val current: SiteDecision)

data class SitePermissionsUiState(
    val sites: List<SitePermissionsSite> = emptyList(),
    val isLoaded: Boolean = false,
    val editing: SitePermissionEdit? = null,
    val isConfirmingRemoveAll: Boolean = false,
)
