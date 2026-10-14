package com.raumanian.thirtysix.browser.core.domain.util

/**
 * Milliseconds from a clock that does not jump when the wall clock is changed, for short
 * windows such as "the same link twice within 2 seconds". A seam so tests need not sleep.
 */
fun interface MonotonicClock {
    fun nowMs(): Long
}
