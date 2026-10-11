package com.raumanian.thirtysix.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Spec 022 US4 — the app-wide text size, as the percentage the web engine takes (FR-031).
 *
 * A view model of its own, like Spec 021's `PopupTabs`, so `BrowserViewModel` does not grow a
 * settings dependency for one number. Only the WebView and the hub's Text size tile read it.
 */
@HiltViewModel
class PageTextSizeViewModel @Inject constructor(observeUserSettings: ObserveUserSettingsUseCase) : ViewModel() {
    val textZoomPercent: StateFlow<Int> = observeUserSettings()
        .map { it.textSize.percent }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppDefaults.TEXT_SIZE.percent,
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
