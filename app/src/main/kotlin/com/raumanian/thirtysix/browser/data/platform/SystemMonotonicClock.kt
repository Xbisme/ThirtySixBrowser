package com.raumanian.thirtysix.browser.data.platform

import android.os.SystemClock
import com.raumanian.thirtysix.browser.core.time.MonotonicClock
import javax.inject.Inject

/** Spec 020 — [MonotonicClock] backed by the time since boot, which a clock change cannot move. */
class SystemMonotonicClock @Inject constructor() : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}
