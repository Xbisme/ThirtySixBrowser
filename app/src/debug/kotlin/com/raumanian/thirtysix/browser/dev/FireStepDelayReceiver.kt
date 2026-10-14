package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.ConfigurableFireStepObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Spec 024 T034 — **debug-build-only** hook for quickstart G7 (interrupted Fire). Sets a delay
 * before every Fire step, so the app can be force-stopped part-way: with `ms = 3000`, step N
 * starts about N × 3 s after confirming (steps in `FireStep` order). Lives in `app/src/debug/`, so it does not exist in a release build.
 *
 * Usage — with the debug build installed:
 * ```
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.FIRE_STEP_DELAY \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.FireStepDelayReceiver \
 *   --el ms 3000
 * adb logcat -s FireStepDelay
 * ```
 * The delay lives in memory, so it is gone after the force-stop and the resumed run is not slowed.
 */
@AndroidEntryPoint
class FireStepDelayReceiver : BroadcastReceiver() {

    @Inject
    lateinit var observer: ConfigurableFireStepObserver

    override fun onReceive(context: Context, intent: Intent) {
        val delayMs = intent.getLongExtra(EXTRA_MS, 0L).coerceAtLeast(0L)
        observer.delayMs = delayMs
        Log.i(LOG_TAG, "fire step delay set to ${delayMs}ms")
    }

    private companion object {
        const val EXTRA_MS = "ms"
        const val LOG_TAG = "FireStepDelay"
    }
}
