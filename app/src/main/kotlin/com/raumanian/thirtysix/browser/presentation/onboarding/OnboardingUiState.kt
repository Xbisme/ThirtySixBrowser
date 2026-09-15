package com.raumanian.thirtysix.browser.presentation.onboarding

import com.raumanian.thirtysix.browser.core.constants.OnboardingSlides
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode

/**
 * Spec 018 — what the onboarding screen renders.
 *
 * The three selections are NOT a copy the screen owns; they are the latest values read back
 * from the settings flow and the language controller (data-model.md INV-1). A local copy would
 * be a second source of truth, and SC-004 — "choices show identically in Settings afterwards" —
 * would become a coincidence rather than a guarantee.
 *
 * [currentSlide] is held in the ViewModel's `SavedStateHandle`, not in this object's lifetime:
 * applying a language recreates the activity and clears the ViewModel store, so a position kept
 * anywhere else resets to the first slide (INV-2, FR-015).
 */
data class OnboardingUiState(
    val currentSlide: Int = OnboardingSlides.FIRST,
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.FollowSystem,
    val searchEngine: SearchEngine = SearchEngine.Google,
) {
    /** Drives the forward control's label: "next" on slides 0–2, a finishing label on the last (FR-002b). */
    val isLastSlide: Boolean get() = currentSlide >= OnboardingSlides.LAST

    /** True on the welcome slide, where Back leaves the app rather than moving backwards (FR-002a). */
    val isFirstSlide: Boolean get() = currentSlide <= OnboardingSlides.FIRST

    /** 1-based position for the progress indicator (FR-003). */
    val displayPosition: Int get() = currentSlide + 1
}
