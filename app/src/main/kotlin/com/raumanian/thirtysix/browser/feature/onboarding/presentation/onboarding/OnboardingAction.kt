package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode

sealed interface OnboardingAction {
    data object Forward : OnboardingAction

    data object Back : OnboardingAction

    data object Skip : OnboardingAction

    data class ThemeSelected(val mode: ThemeMode) : OnboardingAction

    data class SearchEngineSelected(val engine: SearchEngine) : OnboardingAction

    data class LanguageSelected(val language: AppLanguage) : OnboardingAction
}
