package com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
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

/** Sites the person turned protection off for; incognito choices never appear here. */
@HiltViewModel
class ProtectionExceptionsViewModel @Inject constructor(
    exceptions: ProtectionExceptionRepository,
    private val removeExceptions: RemoveProtectionExceptionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProtectionExceptionsUiState())
    val uiState: StateFlow<ProtectionExceptionsUiState> = _uiState.asStateFlow()

    init {
        exceptions.observeAll()
            .onEach { rows -> _uiState.update { it.copy(sites = rows.map { row -> row.site }, isLoaded = true) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ProtectionExceptionsAction) {
        when (action) {
            is ProtectionExceptionsAction.TurnOn -> onTurnOn(action.site)
            ProtectionExceptionsAction.TurnAllOnClick -> onTurnAllOnClick()
            ProtectionExceptionsAction.TurnAllOnConfirmed -> onTurnAllOnConfirmed()
            ProtectionExceptionsAction.TurnAllOnDismissed -> onTurnAllOnDismissed()
        }
    }

    private fun onTurnOn(site: String) {
        viewModelScope.launch { removeExceptions(site) }
    }

    private fun onTurnAllOnClick() = _uiState.update { it.copy(isConfirmingTurnAllOn = true) }

    private fun onTurnAllOnConfirmed() {
        _uiState.update { it.copy(isConfirmingTurnAllOn = false) }
        viewModelScope.launch { removeExceptions(null) }
    }

    private fun onTurnAllOnDismissed() = _uiState.update { it.copy(isConfirmingTurnAllOn = false) }
}
