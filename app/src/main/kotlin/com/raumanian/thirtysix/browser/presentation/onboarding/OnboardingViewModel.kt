package com.raumanian.thirtysix.browser.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.constants.OnboardingSlides
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetOnboardingCompletedUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetThemeModeUseCase
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
 * Spec 018 — view model for the first-run onboarding flow.
 *
 * Every choice is written the moment it is made, through the SAME use cases the Settings
 * screen uses (research.md R4). That reuse is what makes SC-004 — "choices show identically in
 * Settings afterwards" — true by construction rather than by coincidence.
 *
 * ⚠️ THE SLIDE POSITION LIVES IN [SavedStateHandle], NOT IN AN ORDINARY FIELD.
 * Applying a language recreates the activity, which clears the ViewModel store. A position
 * kept in a plain field resets to the first slide — the exact failure FR-015 exists to
 * prevent, and one that passes every rotation test (data-model.md INV-2, research.md R3).
 *
 * ⚠️ IT IS ALSO NOT PERSISTED. The index is session state, not a setting: a user who
 * force-quits mid-flow starts over rather than resuming on slide 3 (INV-4, the FR-012 edge case).
 *
 * ⚠️ THE FIRST-RUN FLAG HAS EXACTLY TWO WRITE SITES — [onForward] on the last slide, and
 * [onSkip]. It is never written from `onCleared()` or any lifecycle hook (INV-6): those fire
 * when the user backs out or leaves, which FR-012 and FR-002a require to leave the flag alone.
 * A user who backed out on slide 1 must still be asked at the next launch.
 */
// LongParameterList is suppressed for the same reason SettingsViewModel suppresses it: these
// are Hilt-injected use cases, one per thing the flow can do. Bundling them into a holder
// would hide the dependency graph without reducing it.
@HiltViewModel
@Suppress("LongParameterList")
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

    /** One-shot "leave the flow" signal. The screen navigates; the view model does not. */
    private val _leaveFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val leaveFlow: SharedFlow<Unit> = _leaveFlow.asSharedFlow()

    init {
        // The selections are read back from the settings snapshot rather than held locally
        // (INV-1), so what the slides show is always what is actually stored.
        observeUserSettings()
            .onEach { settings ->
                _uiState.update {
                    it.copy(themeMode = settings.themeMode, searchEngine = settings.searchEngine)
                }
            }
            .launchIn(viewModelScope)

        refreshAppLanguage()
    }

    // ──────── Navigation within the flow ────────

    /**
     * Advance one slide. On the LAST slide this completes the flow instead — there is no
     * separate finish step; the same control simply carries a finishing label there
     * (FR-002b, FR-010).
     */
    fun onForward() {
        val state = _uiState.value
        if (state.isLastSlide) {
            completeAndLeave()
        } else {
            moveTo(state.currentSlide + 1)
        }
    }

    /**
     * Go back one slide. On the FIRST slide this does nothing and returns `false`, so the
     * screen can let the system close the app — and the first-run flag stays unwritten
     * (FR-002a, FR-012).
     *
     * @return `true` if the flow moved backwards, `false` if the caller should leave the app.
     */
    fun onBack(): Boolean {
        val state = _uiState.value
        if (state.isFirstSlide) return false
        moveTo(state.currentSlide - 1)
        return true
    }

    /**
     * Leave without choosing (FR-011). Writes the first-run flag so the flow does not return,
     * and changes NO setting: skip records that the user was asked, not a preference they never
     * expressed (A4, INV-7). Choices already made on earlier slides are untouched.
     */
    fun onSkip() = completeAndLeave()

    // ──────── Choices — saved the moment they are made (FR-008, FR-009) ────────

    /** Applies immediately and visibly recolours the flow itself (FR-009). */
    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }

    /** Applies immediately (FR-009). */
    fun onSearchEngineSelected(engine: SearchEngine) {
        viewModelScope.launch { setSearchEngine(engine) }
    }

    /**
     * Applies immediately, which RESTARTS THE ACTIVITY (research.md R3).
     *
     * FR-015a is handled upstream: [SetAppLanguageUseCase] returns without calling the platform
     * when the chosen language is already held, so choosing the current language costs no
     * restart and no black flash. The state is refreshed either way, so it is right even where
     * no recreation follows.
     */
    fun onLanguageSelected(language: AppLanguage) {
        viewModelScope.launch {
            setAppLanguage(language)
            refreshAppLanguage()
        }
    }

    // ──────── Internals ────────

    private fun moveTo(slide: Int) {
        val clamped = OnboardingSlides.clamp(slide)
        savedStateHandle[KEY_SLIDE] = clamped
        _uiState.update { it.copy(currentSlide = clamped) }
    }

    /** The only place the first-run flag is written, reached from [onForward] and [onSkip]. */
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

    /** A restored index is clamped rather than trusted (INV-3). */
    private fun restoredSlide(): Int =
        OnboardingSlides.clamp(savedStateHandle[KEY_SLIDE] ?: OnboardingSlides.FIRST)

    private companion object {
        const val KEY_SLIDE = "onboarding_current_slide"
    }
}
