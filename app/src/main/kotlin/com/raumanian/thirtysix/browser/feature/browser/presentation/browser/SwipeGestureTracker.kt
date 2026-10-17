package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import kotlin.math.abs

internal enum class SwipeDirection { Back, Forward }

internal sealed interface SwipeOutput {
    data class Progress(val direction: SwipeDirection, val progress: Float) : SwipeOutput

    data class Commit(val direction: SwipeDirection) : SwipeOutput

    data object Cancel : SwipeOutput
}

/**
 * Turns one touch sequence into a back or forward swipe. A swipe starts only once the drag is
 * mostly sideways, the page has reported overscroll (it did not use the drag itself), and the page
 * cannot scroll further that way. Finger toward the end edge is Back in left-to-right layouts.
 */
internal class SwipeGestureTracker(
    private val touchSlop: Float,
    private val thresholdPx: Float,
    private val isRtl: Boolean,
) {
    private var startX = 0f
    private var startY = 0f
    private var sawOverscroll = false
    private var rejected = false
    private var swipeStartX = 0f
    private var towardEnd = true
    private var direction: SwipeDirection? = null
    private var progress = 0f

    fun down(x: Float, y: Float) {
        startX = x
        startY = y
        sawOverscroll = false
        rejected = false
        direction = null
        progress = 0f
    }

    fun overscrolled() {
        sawOverscroll = true
    }

    /** [canScrollLeft] and [canScrollRight] are the page's own horizontal room right now. */
    fun move(x: Float, y: Float, canScrollLeft: Boolean, canScrollRight: Boolean): SwipeOutput? {
        val current = if (rejected) null else direction ?: begin(x, y, canScrollLeft, canScrollRight)
        return current?.let { swipe ->
            val travelled = if (towardEnd) x - swipeStartX else swipeStartX - x
            progress = (travelled / thresholdPx).coerceIn(0f, 1f)
            SwipeOutput.Progress(swipe, progress)
        }
    }

    fun up(): SwipeOutput? {
        val current = direction ?: return null
        direction = null
        return if (progress >= 1f) SwipeOutput.Commit(current) else SwipeOutput.Cancel
    }

    fun cancel(): SwipeOutput? {
        rejected = true
        return direction?.let {
            direction = null
            SwipeOutput.Cancel
        }
    }

    private fun begin(x: Float, y: Float, canScrollLeft: Boolean, canScrollRight: Boolean): SwipeDirection? {
        val dx = x - startX
        val dy = y - startY
        if (abs(dy) > touchSlop && abs(dy) * 2 > abs(dx)) rejected = true
        val sideways = abs(dx) > touchSlop && abs(dx) >= abs(dy) * 2
        // A finger moving right asks the page to scroll left, so the page must have no room on its left.
        val pageAtEdge = if (dx > 0) !canScrollLeft else !canScrollRight
        val pageUnused = sawOverscroll && pageAtEdge
        if (rejected || !sideways || !pageUnused) return null
        towardEnd = dx > 0
        swipeStartX = x
        val swipe = if (towardEnd != isRtl) SwipeDirection.Back else SwipeDirection.Forward
        direction = swipe
        return swipe
    }
}
