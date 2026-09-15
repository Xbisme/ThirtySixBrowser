package com.raumanian.thirtysix.browser.core.constants

/**
 * Spec 018 — the onboarding flow's fixed slide sequence.
 *
 * Its own file rather than an addition to [BrowserLimits]: these are positions in a fixed
 * sequence, not limits on anything the user can grow. Declared per Constitution §III so no
 * composable carries a literal 0–3 or 4.
 *
 * The ORDER IS NORMATIVE (FR-001): welcome, language, theme, search engine. The welcome slide
 * comes first because it is where the app states what it does not collect, and the three
 * settings that follow are the ones a user plausibly wants different on day one.
 */
object OnboardingSlides {
    /** Index of the welcome slide — app introduction and the privacy statement (FR-004). */
    const val WELCOME: Int = 0

    /** Index of the app-language slide (FR-005). */
    const val LANGUAGE: Int = 1

    /** Index of the theme slide (FR-007). */
    const val THEME: Int = 2

    /** Index of the search-engine slide (FR-007). */
    const val SEARCH_ENGINE: Int = 3

    /** Total number of slides (FR-001). Also the denominator of the progress indicator (FR-003). */
    const val COUNT: Int = 4

    /** Index of the first slide. Back from here leaves the app (FR-002a). */
    const val FIRST: Int = WELCOME

    /**
     * Index of the last slide. Forward from here completes the flow rather than advancing,
     * and the forward control carries a finishing label there (FR-002b).
     */
    const val LAST: Int = COUNT - 1

    /**
     * Clamp a restored slide index into range.
     *
     * A restored index is not trusted (data-model.md INV-3): a corrupted or stale saved-state
     * bundle must not render a slide that does not exist.
     */
    fun clamp(index: Int): Int = index.coerceIn(FIRST, LAST)
}
