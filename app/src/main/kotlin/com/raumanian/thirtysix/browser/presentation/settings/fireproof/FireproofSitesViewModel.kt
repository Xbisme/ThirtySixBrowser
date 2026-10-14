package com.raumanian.thirtysix.browser.presentation.settings.fireproof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.domain.usecase.ObserveFireproofSitesUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RemoveFireproofSitesUseCase
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
 * Spec 024 FR-012 — fireproof sites: remove one, or every one after confirming. Incognito never
 * fireproofs a site (FR-011), so only normal-browsing choices appear here.
 */
@HiltViewModel
class FireproofSitesViewModel @Inject constructor(
    observeSites: ObserveFireproofSitesUseCase,
    private val removeSites: RemoveFireproofSitesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FireproofSitesUiState())
    val uiState: StateFlow<FireproofSitesUiState> = _uiState.asStateFlow()

    init {
        observeSites()
            .onEach { rows -> _uiState.update { it.copy(sites = rows.map { row -> row.site }, isLoaded = true) } }
            .launchIn(viewModelScope)
    }

    fun onRemove(site: String) {
        viewModelScope.launch { removeSites(site) }
    }

    fun onRemoveAllClick() = _uiState.update { it.copy(isConfirmingRemoveAll = true) }

    fun onRemoveAllConfirmed() {
        _uiState.update { it.copy(isConfirmingRemoveAll = false) }
        viewModelScope.launch { removeSites(null) }
    }

    fun onRemoveAllDismissed() = _uiState.update { it.copy(isConfirmingRemoveAll = false) }
}
