package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The settings the page itself follows, in their own view model so [PageViewModel] needs no settings dependency. */
@HiltViewModel
class PageSettingsViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<PageSettingsUiState> = settings.observeSettings()
        .map { PageSettingsUiState(it.textSize.percent, it.isSwipeNavigationEnabled) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PageSettingsUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
