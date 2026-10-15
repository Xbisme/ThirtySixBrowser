package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every choice is saved at once through the same settings store Settings uses, so both screens agree.
 * The slide position lives in [SavedStateHandle]: applying a language clears the ViewModel store.
 * The first-run flag is written only by finishing or skipping, so backing out asks again next launch.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settings: SettingsRepository,
    private val languages: AppLanguageController,
    private val setAppLanguage: SetAppLanguageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(currentSlide = restoredSlide()),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val leaveEvents = MutableSharedFlow<OnboardingEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<OnboardingEvent> = leaveEvents.asSharedFlow()

    init {
        // Read back from the stored settings, so the slides always show what is actually saved.
        settings.observeSettings()
            .onEach { settings ->
                _uiState.update {
                    it.copy(themeMode = settings.themeMode, searchEngine = settings.searchEngine)
                }
            }
            .launchIn(viewModelScope)

        refreshAppLanguage()
    }

    fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Forward -> onForward()
            OnboardingAction.Back -> onBack()
            OnboardingAction.Skip -> completeAndLeave()
            is OnboardingAction.ThemeSelected -> viewModelScope.launch { settings.setThemeMode(action.mode) }
            is OnboardingAction.SearchEngineSelected -> viewModelScope.launch {
                settings.setSearchEngine(action.engine)
            }
            is OnboardingAction.LanguageSelected -> onLanguageSelected(action.language)
        }
    }

    /** On the last slide this completes the flow. */
    private fun onForward() {
        val state = _uiState.value
        if (state.isLastSlide) {
            completeAndLeave()
        } else {
            moveTo(state.currentSlide + 1)
        }
    }

    // On the first slide the screen leaves the app instead, leaving the first-run flag unwritten.
    private fun onBack() {
        val state = _uiState.value
        if (!state.isFirstSlide) moveTo(state.currentSlide - 1)
    }

    // The use case skips the platform when the language is already held; refresh either way.
    private fun onLanguageSelected(language: AppLanguage) {
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

    // Skip records that the person was asked; it changes no setting.
    private fun completeAndLeave() {
        viewModelScope.launch {
            settings.setOnboardingCompleted(true)
            leaveEvents.tryEmit(OnboardingEvent.Leave)
        }
    }

    private fun refreshAppLanguage() {
        viewModelScope.launch {
            _uiState.update { it.copy(language = languages.current()) }
        }
    }

    // A restored index is clamped rather than trusted.
    private fun restoredSlide(): Int =
        OnboardingSlides.clamp(savedStateHandle[KEY_SLIDE] ?: OnboardingSlides.FIRST)

    private companion object {
        const val KEY_SLIDE = "onboarding_current_slide"
    }
}
