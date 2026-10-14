package com.raumanian.thirtysix.browser.domain.fire

import com.raumanian.thirtysix.browser.domain.model.FireStep
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Spec 024 T034 — waits [delayMs] before every Fire step; `0` (the default, and always in
 * release builds) waits for nothing. Only the debug source set's `FireStepDelayReceiver` ever
 * sets it, so quickstart G7 can stop the app between two known steps.
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
