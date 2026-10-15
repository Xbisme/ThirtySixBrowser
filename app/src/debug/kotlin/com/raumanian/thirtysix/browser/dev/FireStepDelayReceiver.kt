package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.ConfigurableFireStepObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Debug-only: delays every Fire step by `--el ms N` so the app can be force-stopped part-way. The
 * delay is held in memory, so the resumed run after the force-stop is not slowed.
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
