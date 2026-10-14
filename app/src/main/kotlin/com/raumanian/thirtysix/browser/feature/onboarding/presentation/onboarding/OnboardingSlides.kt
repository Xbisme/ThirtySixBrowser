package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

/** The fixed slide order. The welcome slide comes first because it states what the app does not collect. */
object OnboardingSlides {
    const val WELCOME: Int = 0

    const val LANGUAGE: Int = 1

    const val THEME: Int = 2

    const val SEARCH_ENGINE: Int = 3

    const val COUNT: Int = 4

    const val FIRST: Int = WELCOME

    const val LAST: Int = COUNT - 1

    /** A stale or corrupted saved-state bundle must not render a slide that does not exist. */
    fun clamp(index: Int): Int = index.coerceIn(FIRST, LAST)
}
