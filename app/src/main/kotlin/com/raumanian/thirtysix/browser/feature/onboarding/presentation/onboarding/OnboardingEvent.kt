package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

sealed interface OnboardingEvent {
    data object Leave : OnboardingEvent
}
