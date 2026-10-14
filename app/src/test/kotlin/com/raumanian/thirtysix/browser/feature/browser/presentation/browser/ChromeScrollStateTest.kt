package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 019 T035 — contracts C2. */
class ChromeScrollStateTest {

    @Test
    fun scrollingDown_hidesUpToTheMaximum() {
        val state = ChromeScrollState(MAX)
        state.onScrollDelta(PART)
        assertEquals(PART, state.offsetPx, DELTA)
        state.onScrollDelta(MAX * 2)
        assertEquals(MAX, state.offsetPx, DELTA)
        assertEquals(1f, state.hiddenFraction, DELTA)
    }

    @Test
    fun scrollingUp_showsButNeverBelowZero() {
        val state = ChromeScrollState(MAX)
        state.onScrollDelta(MAX)
        state.onScrollDelta(-PART)
        assertEquals(MAX - PART, state.offsetPx, DELTA)
        state.onScrollDelta(-MAX * 2)
        assertEquals(0f, state.offsetPx, DELTA)
    }

    @Test
    fun show_returnsToZero() {
        val state = ChromeScrollState(MAX)
        state.onScrollDelta(MAX)
        state.show()
        assertEquals(0f, state.offsetPx, DELTA)
        assertEquals(0f, state.hiddenFraction, DELTA)
    }

    @Test
    fun shrinkingTheMaximum_clampsTheOffset() {
        val state = ChromeScrollState(MAX)
        state.onScrollDelta(MAX)
        state.maxOffsetPx = PART
        assertEquals(PART, state.offsetPx, DELTA)
    }

    @Test
    fun zeroMaximum_neverHides() {
        val state = ChromeScrollState(0f)
        state.onScrollDelta(PART)
        assertEquals(0f, state.offsetPx, DELTA)
        assertEquals(0f, state.hiddenFraction, DELTA)
    }

    private companion object {
        const val MAX = 300f
        const val PART = 120f
        const val DELTA = 0.001f
    }
}
