package com.raumanian.thirtysix.browser.core.time

/**
 * Spec 020 research R6 — milliseconds from a clock that never jumps when the wall clock is
 * changed. Used for short windows such as "the same link twice within 2 seconds" (FR-007).
 * A seam so the window can be tested without sleeping.
 */
fun interface MonotonicClock {
    fun nowMs(): Long
}
