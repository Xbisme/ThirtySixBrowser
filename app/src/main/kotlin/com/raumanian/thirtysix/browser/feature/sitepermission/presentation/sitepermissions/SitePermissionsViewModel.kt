package com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.feature.sitepermission.domain.usecase.RemoveSitePermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SitePermissionsViewModel @Inject constructor(
    private val repository: SitePermissionRepository,
    private val removeSitePermissions: RemoveSitePermissionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SitePermissionsUiState())
    val uiState: StateFlow<SitePermissionsUiState> = _uiState.asStateFlow()

    init {
        repository.observeAll()
            .onEach { decisions -> _uiState.update { it.copy(sites = group(decisions), isLoaded = true) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SitePermissionsAction) {
        when (action) {
            is SitePermissionsAction.DecisionClick -> onDecisionClick(action.origin, action.capability, action.current)
            is SitePermissionsAction.DecisionChosen -> onDecisionChosen(action.decision)
            SitePermissionsAction.EditDismissed -> onEditDismissed()
            is SitePermissionsAction.RemoveSite -> onRemoveSite(action.origin)
            SitePermissionsAction.RemoveAllClick -> onRemoveAllClick()
            SitePermissionsAction.RemoveAllConfirmed -> onRemoveAllConfirmed()
            SitePermissionsAction.RemoveAllDismissed -> onRemoveAllDismissed()
        }
    }

    private fun onDecisionClick(origin: String, capability: SiteCapability, current: SiteDecision) =
        _uiState.update { it.copy(editing = SitePermissionEdit(origin, capability, current)) }

    private fun onDecisionChosen(decision: SiteDecision) {
        val edit = _uiState.value.editing ?: return
        _uiState.update { it.copy(editing = null) }
        if (decision != edit.current) {
            viewModelScope.launch { repository.save(edit.origin, edit.capability, decision) }
        }
    }

    private fun onEditDismissed() = _uiState.update { it.copy(editing = null) }

    private fun onRemoveSite(origin: String) {
        viewModelScope.launch { removeSitePermissions(origin) }
    }

    private fun onRemoveAllClick() = _uiState.update { it.copy(isConfirmingRemoveAll = true) }

    private fun onRemoveAllConfirmed() {
        _uiState.update { it.copy(isConfirmingRemoveAll = false) }
        viewModelScope.launch { removeSitePermissions(null) }
    }

    private fun onRemoveAllDismissed() = _uiState.update { it.copy(isConfirmingRemoveAll = false) }

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
