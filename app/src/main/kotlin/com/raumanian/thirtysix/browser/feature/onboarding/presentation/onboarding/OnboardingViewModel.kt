package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetThemeModeUseCase
import com.raumanian.thirtysix.browser.feature.onboarding.domain.usecase.SetOnboardingCompletedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every choice is saved at once through the same use cases Settings uses, so both screens agree.
 * The slide position lives in [SavedStateHandle]: applying a language clears the ViewModel store.
 * The first-run flag is written only by finishing or skipping, so backing out asks again next launch.
 */
@HiltViewModel
@Suppress("LongParameterList") // one injected use case per thing the flow can do
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeUserSettings: ObserveUserSettingsUseCase,
    private val getAppLanguage: GetAppLanguageUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val setSearchEngine: SetSearchEngineUseCase,
    private val setAppLanguage: SetAppLanguageUseCase,
    private val setOnboardingCompleted: SetOnboardingCompletedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(currentSlide = restoredSlide()),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _leaveFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val leaveFlow: SharedFlow<Unit> = _leaveFlow.asSharedFlow()

    init {
        // Read back from the stored settings, so the slides always show what is actually saved.
        observeUserSettings()
            .onEach { settings ->
                _uiState.update {
                    it.copy(themeMode = settings.themeMode, searchEngine = settings.searchEngine)
                }
            }
            .launchIn(viewModelScope)

        refreshAppLanguage()
    }

    /** On the last slide this completes the flow. */
    fun onForward() {
        val state = _uiState.value
        if (state.isLastSlide) {
            completeAndLeave()
        } else {
            moveTo(state.currentSlide + 1)
        }
    }

    /** @return `false` on the first slide: the caller should leave the app, leaving the first-run flag unwritten. */
    fun onBack(): Boolean {
        val state = _uiState.value
        if (state.isFirstSlide) return false
        moveTo(state.currentSlide - 1)
        return true
    }

    // Skip records that the person was asked; it changes no setting.
    fun onSkip() = completeAndLeave()

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }

    fun onSearchEngineSelected(engine: SearchEngine) {
        viewModelScope.launch { setSearchEngine(engine) }
    }

    // The use case skips the platform when the language is already held; refresh either way.
    fun onLanguageSelected(language: AppLanguage) {
        viewModelScope.launch {
            setAppLanguage(language)
            refreshAppLanguage()
        }
    }

    private fun moveTo(slide: Int) {
        val clamped = OnboardingSlides.clamp(slide)
        savedStateHandle[KEY_SLIDE] = clamped
        _uiState.update { it.copy(currentSlide = clamped) }
    }

    private fun completeAndLeave() {
        viewModelScope.launch {
            setOnboardingCompleted(true)
            _leaveFlow.tryEmit(Unit)
        }
    }

    private fun refreshAppLanguage() {
        viewModelScope.launch {
            _uiState.update { it.copy(language = getAppLanguage()) }
        }
    }

    // A restored index is clamped rather than trusted.
    private fun restoredSlide(): Int =
        OnboardingSlides.clamp(savedStateHandle[KEY_SLIDE] ?: OnboardingSlides.FIRST)

    private companion object {
        const val KEY_SLIDE = "onboarding_current_slide"
    }
}
