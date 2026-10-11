package com.raumanian.thirtysix.browser.presentation.settings.sitepermissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.domain.usecase.ObserveSitePermissionsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RemoveSitePermissionsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSitePermissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Spec 021 FR-009 / C2.7 — the Site permissions screen: remembered decisions grouped by site,
 * change one, remove a site, remove all (after confirmation).
 */
@HiltViewModel
class SitePermissionsViewModel @Inject constructor(
    observeSitePermissions: ObserveSitePermissionsUseCase,
    private val setSitePermission: SetSitePermissionUseCase,
    private val removeSitePermissions: RemoveSitePermissionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SitePermissionsUiState())
    val uiState: StateFlow<SitePermissionsUiState> = _uiState.asStateFlow()

    init {
        observeSitePermissions()
            .onEach { decisions -> _uiState.update { it.copy(sites = group(decisions), isLoaded = true) } }
            .launchIn(viewModelScope)
    }

    fun onDecisionClick(origin: String, capability: SiteCapability, current: SiteDecision) =
        _uiState.update { it.copy(editing = SitePermissionEdit(origin, capability, current)) }

    fun onDecisionChosen(decision: SiteDecision) {
        val edit = _uiState.value.editing ?: return
        _uiState.update { it.copy(editing = null) }
        if (decision != edit.current) {
            viewModelScope.launch { setSitePermission(edit.origin, edit.capability, decision) }
        }
    }

    fun onEditDismissed() = _uiState.update { it.copy(editing = null) }

    fun onRemoveSite(origin: String) {
        viewModelScope.launch { removeSitePermissions(origin) }
    }

    fun onRemoveAllClick() = _uiState.update { it.copy(isConfirmingRemoveAll = true) }

    fun onRemoveAllConfirmed() {
        _uiState.update { it.copy(isConfirmingRemoveAll = false) }
        viewModelScope.launch { removeSitePermissions(null) }
    }

    fun onRemoveAllDismissed() = _uiState.update { it.copy(isConfirmingRemoveAll = false) }

    private fun group(decisions: List<SitePermissionDecision>): List<SitePermissionsSite> =
        decisions.groupBy { it.origin }
            .map { (origin, list) ->
                SitePermissionsSite(
                    origin = origin,
                    decisions = list.sortedBy { it.capability.ordinal }.map { it.capability to it.decision },
                )
            }
            .sortedBy { it.origin }
}
