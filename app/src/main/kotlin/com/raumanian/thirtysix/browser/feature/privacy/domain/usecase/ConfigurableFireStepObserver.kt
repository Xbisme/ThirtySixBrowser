package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.FireStep
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Waits [delayMs] before every Fire step; `0` in release builds. Only the debug-only
 * `FireStepDelayReceiver` sets it, to stop the app between two known steps in manual testing.
 */
@Singleton
class ConfigurableFireStepObserver @Inject constructor() : FireStepObserver {
    @Volatile
    var delayMs: Long = 0L

    override suspend fun beforeStep(step: FireStep) {
        val wait = delayMs
        if (wait > 0L) delay(wait)
    }
}
