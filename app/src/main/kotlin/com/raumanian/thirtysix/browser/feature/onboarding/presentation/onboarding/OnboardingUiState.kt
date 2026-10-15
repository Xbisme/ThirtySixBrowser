package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
/**
 * The selections are the latest stored values, not a copy the screen owns, so onboarding and
 * Settings cannot disagree.
 */
data class OnboardingUiState(
    val currentSlide: Int = OnboardingSlides.FIRST,
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.FollowSystem,
    val searchEngine: SearchEngine = SearchEngine.Google,
) {
    val isLastSlide: Boolean get() = currentSlide >= OnboardingSlides.LAST

    val isFirstSlide: Boolean get() = currentSlide <= OnboardingSlides.FIRST

    val displayPosition: Int get() = currentSlide + 1
}
