package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

internal object TabSwipeLimits {
    /** Share of the address bar's width a release must pass to switch. */
    const val COMMIT_FRACTION: Float = 0.33f

    const val FLICK_VELOCITY_DP_PER_S: Float = 1_000f

    /** How far the bar follows the finger toward a side with no tab. */
    const val EDGE_RESISTANCE: Float = 0.25f

    /** A drag counts as sideways once its horizontal travel is this many times its vertical travel. */
    const val SIDEWAYS_RATIO: Float = 2f

    /** The incoming tab's preview covers the page at most this long after a switch. */
    const val PREVIEW_HOLD_MAX_MS: Long = 600L
}
