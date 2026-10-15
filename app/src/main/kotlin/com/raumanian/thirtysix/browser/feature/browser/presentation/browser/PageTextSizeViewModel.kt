package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The app-wide text size, in its own view model so [PageViewModel] needs no settings dependency. */
@HiltViewModel
class PageTextSizeViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val textZoomPercent: StateFlow<Int> = settings.observeSettings()
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
