package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * How far the bars have slid away, 0 (shown) to [maxOffsetPx] (hidden). Read it only inside
 * `Modifier.offset { }` or `graphicsLayer { }`: a read in a composable body recomposes on every frame.
 */
@Stable
class ChromeScrollState(maxOffsetPx: Float) {
    var maxOffsetPx: Float = maxOffsetPx.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            offsetPx = offsetPx.coerceIn(0f, field)
        }

    var offsetPx: Float by mutableFloatStateOf(0f)
        private set

    val hiddenFraction: Float
        get() = if (maxOffsetPx == 0f) 0f else offsetPx / maxOffsetPx

    /** [dy] > 0 means the user scrolled down: hide the bars. */
    fun onScrollDelta(dy: Float) {
        offsetPx = (offsetPx + dy).coerceIn(0f, maxOffsetPx)
    }

    fun show() {
        offsetPx = 0f
    }
}
