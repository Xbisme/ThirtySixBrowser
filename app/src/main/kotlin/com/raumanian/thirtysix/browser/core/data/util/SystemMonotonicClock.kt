package com.raumanian.thirtysix.browser.core.data.util

import android.os.SystemClock
import com.raumanian.thirtysix.browser.core.domain.util.MonotonicClock
import javax.inject.Inject

/** [MonotonicClock] backed by the time since boot, which a clock change cannot move. */
class SystemMonotonicClock @Inject constructor() : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}
