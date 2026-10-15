package com.raumanian.thirtysix.browser.feature.privacy.presentation.fireproof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.RemoveFireproofSitesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Incognito never fireproofs a site, so only normal-browsing choices appear here. */
@HiltViewModel
class FireproofSitesViewModel @Inject constructor(
    sites: FireproofSiteRepository,
    private val removeSites: RemoveFireproofSitesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FireproofSitesUiState())
    val uiState: StateFlow<FireproofSitesUiState> = _uiState.asStateFlow()

    init {
        sites.observeAll()
            .onEach { rows -> _uiState.update { it.copy(sites = rows.map { row -> row.site }, isLoaded = true) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: FireproofSitesAction) {
        when (action) {
            is FireproofSitesAction.Remove -> onRemove(action.site)
            FireproofSitesAction.RemoveAllClick -> onRemoveAllClick()
            FireproofSitesAction.RemoveAllConfirmed -> onRemoveAllConfirmed()
            FireproofSitesAction.RemoveAllDismissed -> onRemoveAllDismissed()
        }
    }

    private fun onRemove(site: String) {
        viewModelScope.launch { removeSites(site) }
    }

    private fun onRemoveAllClick() = _uiState.update { it.copy(isConfirmingRemoveAll = true) }

    private fun onRemoveAllConfirmed() {
        _uiState.update { it.copy(isConfirmingRemoveAll = false) }
        viewModelScope.launch { removeSites(null) }
    }

    private fun onRemoveAllDismissed() = _uiState.update { it.copy(isConfirmingRemoveAll = false) }
}
