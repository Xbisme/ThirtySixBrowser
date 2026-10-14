package com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.ObserveProtectionExceptionsUseCase
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.RemoveProtectionExceptionsUseCase
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
 * Spec 023 FR-015 — sites the person turned protection off for: turn one back on, or every one
 * after confirming. Incognito choices never appear here (FR-014).
 */
@HiltViewModel
class ProtectionExceptionsViewModel @Inject constructor(
    observeExceptions: ObserveProtectionExceptionsUseCase,
    private val removeExceptions: RemoveProtectionExceptionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProtectionExceptionsUiState())
    val uiState: StateFlow<ProtectionExceptionsUiState> = _uiState.asStateFlow()

    init {
        observeExceptions()
            .onEach { rows -> _uiState.update { it.copy(sites = rows.map { row -> row.site }, isLoaded = true) } }
            .launchIn(viewModelScope)
    }

    fun onTurnOn(site: String) {
        viewModelScope.launch { removeExceptions(site) }
    }

    fun onTurnAllOnClick() = _uiState.update { it.copy(isConfirmingTurnAllOn = true) }

    fun onTurnAllOnConfirmed() {
        _uiState.update { it.copy(isConfirmingTurnAllOn = false) }
        viewModelScope.launch { removeExceptions(null) }
    }

    fun onTurnAllOnDismissed() = _uiState.update { it.copy(isConfirmingTurnAllOn = false) }
}
