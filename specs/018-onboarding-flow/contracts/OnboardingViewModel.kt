package com.raumanian.thirtysix.browser.presentation.onboarding

/*
 * Spec 018 — the onboarding screen's contract.
 *
 * Shape follows the project's established MVVM: an immutable UiState data class exposed as
 * StateFlow, mutated only through MutableStateFlow.update { } (Constitution §III/V). The
 * save-on-select handlers mirror SettingsViewModel's (research.md R4) and deliberately reuse
 * the SAME use cases, which is what makes SC-004 — "choices show identically in Settings" —
 * true by construction rather than by coincidence.
 */

/**
 * What the screen renders.
 *
 * The three selections are NOT stored here as independent fields the screen owns; they are
 * the latest values read back from the settings flow and the language controller
 * (data-model.md INV-1). A local copy would be a second source of truth.
 *
 * `currentSlide` is the exception, and it does NOT live in this state alone — see the note
 * on slide position below.
 */
data class OnboardingUiStateContract(
    val currentSlide: Int,          // 0..3, clamped on restore (INV-3)
    val isLastSlide: Boolean,       // drives the forward control's label (FR-002b, INV-13)
    val themeMode: Any,             // ThemeMode — current value, read back
    val language: Any,              // AppLanguage — current value, read back
    val searchEngine: Any,          // SearchEngine — current value, read back
)

interface OnboardingViewModelContract {

    // --- Navigation within the flow -------------------------------------------------

    /** Advance one slide. On the LAST slide this completes the flow (FR-002b, FR-010). */
    fun onForward()

    /**
     * Go back one slide. On the FIRST slide the caller leaves the app instead, and the
     * first-run flag is NOT written (FR-002a, FR-012, INV-6).
     */
    fun onBack()

    /**
     * Leave without choosing. Writes the first-run flag (FR-011) and changes NO setting
     * (FR-011, A4, INV-7) — skip records that the user was asked, not a preference they
     * never expressed.
     */
    fun onSkip()

    // --- Choices: saved the moment they are made (FR-008, FR-009) --------------------

    /** Applies immediately and visibly recolours the flow itself (FR-009). */
    fun onThemeSelected(mode: Any)

    /** Applies immediately (FR-009). */
    fun onSearchEngineSelected(engine: Any)

    /**
     * Applies immediately, which RESTARTS THE ACTIVITY (research.md R3).
     *
     * MUST NOT apply when the chosen language already matches the current one, or the user
     * pays a 111–288 ms black flash for a no-op (FR-015a). Compare against the language
     * controller's `current()` first.
     */
    fun onLanguageSelected(language: Any)
}

/*
 * ⚠️ SLIDE POSITION IS NOT AN ORDINARY VIEWMODEL FIELD.
 *
 * Applying a language recreates the activity. A plain ViewModel field does not survive that
 * — the ViewModel store is cleared — so the user lands back on slide 1, which is precisely
 * the failure FR-015 exists to prevent (research.md R3, data-model.md INV-2).
 *
 * The position MUST be held in saved instance state: either `rememberSaveable` in the
 * composable, or a `SavedStateHandle` in this ViewModel. Both survive rotation (FR-016) AND
 * activity recreation (FR-015). Choosing a plain field passes every test that only rotates.
 *
 * Restored values are clamped to 0..3 (INV-3) rather than trusted.
 */

/*
 * ⚠️ THE FIRST-RUN FLAG HAS EXACTLY TWO WRITE SITES: onForward() on the last slide, and
 * onSkip(). It MUST NOT be written from onCleared(), DisposableEffect disposal, onStop, or
 * any other lifecycle hook (INV-6). Those fire when the user backs out or leaves — which
 * FR-012 and FR-002a require to leave the flag untouched. The tempting smaller
 * implementation, "write it when the screen goes away", is exactly the wrong one: a user who
 * backed out on slide 1 would never be asked again.
 */

/*
 * ⚠️ LEAVING THE FLOW MUST POP IT INCLUSIVELY.
 *
 * Both exits navigate to the browser popping the onboarding route off the stack inclusively,
 * so Back from the browser's first page leaves the app rather than returning to onboarding
 * (FR-010a, research.md R6). A test that only asserts "the browser is showing" will not catch
 * a missing pop — SC-015 presses Back, and that is the check that matters.
 */
