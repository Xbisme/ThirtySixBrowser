package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import kotlin.math.abs
import kotlin.math.sign

internal enum class TabSide { Previous, Next }

/** The side, as a sign of x, that a tab on this side enters from: the next tab from the end edge. */
internal fun TabSide.entrySign(rtl: Boolean): Float = if ((this == TabSide.Next) != rtl) 1f else -1f

internal sealed interface TabSwipeOutput {
    /** [offsetPx] is how far the bar has moved, already damped toward a side with no tab. */
    data class Drag(val offsetPx: Float, val side: TabSide) : TabSwipeOutput

    data class Commit(val side: TabSide) : TabSwipeOutput

    data object Return : TabSwipeOutput
}

/**
 * Turns one touch sequence on the address bar into a tab swipe. It claims the touch only once the
 * drag is clearly sideways, so a tap or a slight drift still reaches the text field. Finger toward
 * the start edge brings in the next tab.
 */
internal class TabSwipeTracker(
    private val touchSlop: Float,
    private val laneWidthPx: Float,
    private val flickVelocityPxPerS: Float,
    private val isRtl: Boolean,
) {
    private var startX = 0f
    private var startY = 0f
    private var canStart = false
    private var hasPrevious = false
    private var hasNext = false
    private var rejected = false
    private var offset = 0f

    var claimed: Boolean = false
        private set

    fun down(x: Float, y: Float, canStart: Boolean, hasPrevious: Boolean, hasNext: Boolean) {
        startX = x
        startY = y
        this.canStart = canStart
        this.hasPrevious = hasPrevious
        this.hasNext = hasNext
        rejected = !canStart
        claimed = false
        offset = 0f
    }

    fun move(x: Float, y: Float): TabSwipeOutput? {
        val dx = x - startX
        val dy = y - startY
        if (!claimed && !rejected) claim(dx, dy)
        if (!claimed) return null
        val travel = dx - dx.sign * touchSlop
        val side = sideFor(travel)
        offset = if (hasTab(side)) travel else travel * TabSwipeLimits.EDGE_RESISTANCE
        return TabSwipeOutput.Drag(offset, side)
    }

    fun up(velocityX: Float): TabSwipeOutput? {
        if (!claimed) return null
        claimed = false
        val side = sideFor(offset)
        val far = abs(offset) >= laneWidthPx * TabSwipeLimits.COMMIT_FRACTION
        val flickSide = sideFor(velocityX)
        val flick = abs(velocityX) >= flickVelocityPxPerS && flickSide == side
        return if (hasTab(side) && (far || flick)) TabSwipeOutput.Commit(side) else TabSwipeOutput.Return
    }

    /** A second finger, a change of the incoming tab, or the system taking the touch. */
    fun cancel(): TabSwipeOutput? {
        rejected = true
        if (!claimed) return null
        claimed = false
        return TabSwipeOutput.Return
    }

    private fun claim(dx: Float, dy: Float) {
        if (abs(dy) > touchSlop && abs(dy) * TabSwipeLimits.SIDEWAYS_RATIO > abs(dx)) {
            rejected = true
        } else if (abs(dx) > touchSlop && abs(dx) >= abs(dy) * TabSwipeLimits.SIDEWAYS_RATIO) {
            claimed = true
        }
    }

    /** In a left-to-right layout a finger moving left (negative travel) pulls in the next tab. */
    private fun sideFor(travel: Float): TabSide = if ((travel < 0f) != isRtl) TabSide.Next else TabSide.Previous

    private fun hasTab(side: TabSide): Boolean = if (side == TabSide.Next) hasNext else hasPrevious
}
