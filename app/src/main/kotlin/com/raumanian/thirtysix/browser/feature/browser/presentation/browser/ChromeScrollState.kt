@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Spec 019 — how far the browsing bars have slid away (research R7, contracts C2).
 *
 * [offsetPx] is 0 when both bars are fully shown and [maxOffsetPx] when they are fully hidden.
 * It is snapshot state, but it MUST be read only inside `Modifier.offset { }` or
 * `graphicsLayer { }` (Constitution v1.4.0 §V rule 1): reading it in a composable body would
 * recompose the whole browser on every scroll frame. Scroll deltas allocate nothing.
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

    /** Fraction hidden, 0f..1f. Same read rules as [offsetPx]. */
    val hiddenFraction: Float
        get() = if (maxOffsetPx == 0f) 0f else offsetPx / maxOffsetPx

    /** [dy] > 0 means the page content moved up (user scrolled down): hide the bars. */
    fun onScrollDelta(dy: Float) {
        offsetPx = (offsetPx + dy).coerceIn(0f, maxOffsetPx)
    }

    /** Bring both bars fully back. */
    fun show() {
        offsetPx = 0f
    }
}

@Composable
fun rememberChromeScrollState(maxOffsetPx: Float): ChromeScrollState {
    val state = remember { ChromeScrollState(maxOffsetPx) }
    state.maxOffsetPx = maxOffsetPx
    return state
}
