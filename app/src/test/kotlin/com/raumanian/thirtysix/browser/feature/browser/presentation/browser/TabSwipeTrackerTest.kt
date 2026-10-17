package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSwipeTrackerTest {

    private fun tracker(rtl: Boolean = false) = TabSwipeTracker(
        touchSlop = SLOP,
        laneWidthPx = WIDTH,
        flickVelocityPxPerS = FLICK,
        isRtl = rtl,
    )

    private fun TabSwipeTracker.start(canStart: Boolean = true, previous: Boolean = true, next: Boolean = true) =
        apply { down(START, START, canStart, previous, next) }

    @Test
    fun `no claim under the touch slop`() {
        val t = tracker().start()

        assertNull(t.move(START - SLOP * 0.9f, START))
        assertFalse(t.claimed)
        assertNull(t.up(velocityX = 0f))
    }

    @Test
    fun `a diagonal drag is never claimed`() {
        val t = tracker().start()

        assertNull(t.move(START - 40f, START + 40f))
        assertNull(t.move(START - 300f, START + 40f))
        assertFalse(t.claimed)
    }

    @Test
    fun `a clearly sideways drag is claimed and follows the finger past the slop`() {
        val t = tracker().start()

        val drag = t.move(START - 3 * SLOP, START + SLOP) as TabSwipeOutput.Drag

        assertTrue(t.claimed)
        assertEquals(-2 * SLOP, drag.offsetPx, 0.01f)
        assertEquals(TabSide.Next, drag.side)
    }

    @Test
    fun `release past a third of the width switches, short of it returns`() {
        val far = tracker().start()
        far.move(START - (0.34f * WIDTH + SLOP), START)
        assertEquals(TabSwipeOutput.Commit(TabSide.Next), far.up(velocityX = 0f))

        val short = tracker().start()
        short.move(START + (0.30f * WIDTH + SLOP), START)
        assertEquals(TabSwipeOutput.Return, short.up(velocityX = 0f))
    }

    @Test
    fun `a short fast flick toward a tab switches, a flick back the other way does not`() {
        val toward = tracker().start()
        toward.move(START + 4 * SLOP, START)
        assertEquals(TabSwipeOutput.Commit(TabSide.Previous), toward.up(velocityX = FLICK + 1f))

        val against = tracker().start()
        against.move(START + 4 * SLOP, START)
        assertEquals(TabSwipeOutput.Return, against.up(velocityX = -(FLICK + 1f)))
    }

    @Test
    fun `toward a side with no tab the bar is damped and always returns`() {
        val t = tracker().start(next = false)

        val drag = t.move(START - (0.8f * WIDTH + SLOP), START) as TabSwipeOutput.Drag

        assertEquals(-0.8f * WIDTH * TabSwipeLimits.EDGE_RESISTANCE, drag.offsetPx, 0.01f)
        assertEquals(TabSwipeOutput.Return, t.up(velocityX = -(FLICK * 2)))
    }

    @Test
    fun `a second finger or a cancel returns`() {
        val t = tracker().start()
        t.move(START - 0.5f * WIDTH, START)

        assertEquals(TabSwipeOutput.Return, t.cancel())
        assertNull(t.move(START - 0.9f * WIDTH, START))
        assertNull(t.up(velocityX = 0f))
    }

    @Test
    fun `right to left mirrors which side is next`() {
        val t = tracker(rtl = true).start()

        val drag = t.move(START + 3 * SLOP, START) as TabSwipeOutput.Drag

        assertEquals(TabSide.Next, drag.side)
    }

    @Test
    fun `when it cannot start, nothing is ever claimed`() {
        val t = tracker().start(canStart = false)

        assertNull(t.move(START - 0.9f * WIDTH, START))
        assertFalse(t.claimed)
    }

    @Test
    fun `a drag that starts vertical stays rejected after it turns sideways`() {
        val t = tracker().start()

        assertNull(t.move(START, START + 3 * SLOP))
        assertNull(t.move(START - 0.9f * WIDTH, START + 3 * SLOP))
        assertFalse(t.claimed)
    }

    private companion object {
        const val SLOP = 10f
        const val WIDTH = 1_000f
        const val FLICK = 2_500f
        const val START = 500f
    }
}
