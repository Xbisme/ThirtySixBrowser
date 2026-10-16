package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeGestureTrackerTest {

    private fun tracker(rtl: Boolean = false) =
        SwipeGestureTracker(touchSlop = SLOP, thresholdPx = THRESHOLD, isRtl = rtl).also { it.down(START, START) }

    private fun SwipeGestureTracker.drag(dx: Float, dy: Float = 0f, left: Boolean = false, right: Boolean = false) =
        move(START + dx, START + dy, canScrollLeft = left, canScrollRight = right)

    @Test
    fun `a sideways drag the page did not use starts a back swipe toward the end edge`() {
        val tracker = tracker()
        tracker.overscrolled()

        assertEquals(SwipeOutput.Progress(SwipeDirection.Back, 0f), tracker.drag(dx = 20f))
        assertEquals(SwipeOutput.Progress(SwipeDirection.Back, 0.5f), tracker.drag(dx = 20f + THRESHOLD / 2))
    }

    @Test
    fun `toward the start edge is forward`() {
        val tracker = tracker()
        tracker.overscrolled()

        assertEquals(SwipeDirection.Forward, (tracker.drag(dx = -20f) as SwipeOutput.Progress).direction)
    }

    @Test
    fun `right-to-left layouts mirror the directions`() {
        val toEnd = tracker(rtl = true).apply { overscrolled() }
        val toStart = tracker(rtl = true).apply { overscrolled() }

        assertEquals(SwipeDirection.Forward, (toEnd.drag(dx = 20f) as SwipeOutput.Progress).direction)
        assertEquals(SwipeDirection.Back, (toStart.drag(dx = -20f) as SwipeOutput.Progress).direction)
    }

    @Test
    fun `releasing past the threshold commits and releasing before it cancels`() {
        val committed = tracker().apply { overscrolled() }
        committed.drag(dx = 20f)
        committed.drag(dx = 20f + THRESHOLD * 2)
        assertEquals(SwipeOutput.Commit(SwipeDirection.Back), committed.up())

        val cancelled = tracker().apply { overscrolled() }
        cancelled.drag(dx = 20f)
        cancelled.drag(dx = 20f + THRESHOLD / 3)
        assertEquals(SwipeOutput.Cancel, cancelled.up())
    }

    @Test
    fun `progress is clamped between zero and one`() {
        val tracker = tracker().apply { overscrolled() }
        tracker.drag(dx = 20f)

        assertEquals(SwipeOutput.Progress(SwipeDirection.Back, 1f), tracker.drag(dx = 20f + THRESHOLD * 3))
        assertEquals(SwipeOutput.Progress(SwipeDirection.Back, 0f), tracker.drag(dx = 0f))
    }

    @Test
    fun `no swipe without an overscroll report`() {
        val tracker = tracker()

        assertNull(tracker.drag(dx = 40f))
        assertNull(tracker.up())
    }

    @Test
    fun `no swipe while the page can still scroll that way`() {
        val tracker = tracker().apply { overscrolled() }

        assertNull(tracker.drag(dx = 40f, left = true))
    }

    @Test
    fun `a mostly vertical drag is a scroll for the rest of the touch`() {
        val tracker = tracker().apply { overscrolled() }

        assertNull(tracker.drag(dx = 5f, dy = 40f))
        assertNull(tracker.drag(dx = 200f, dy = 40f))
    }

    @Test
    fun `a drag within the touch slop does nothing yet`() {
        val tracker = tracker().apply { overscrolled() }

        assertNull(tracker.drag(dx = SLOP / 2))
    }

    @Test
    fun `a second finger cancels the swipe`() {
        val tracker = tracker().apply { overscrolled() }
        tracker.drag(dx = 20f)

        assertEquals(SwipeOutput.Cancel, tracker.cancel())
        assertNull(tracker.drag(dx = 200f))
    }

    private companion object {
        const val SLOP = 10f
        const val THRESHOLD = 200f
        const val START = 500f
    }
}
