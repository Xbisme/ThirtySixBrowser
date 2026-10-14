package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.FireStep
/**
 * Spec 024 T034 — called before each Fire step. The production binding does nothing unless a
 * debug-only receiver has set a delay, which quickstart G7 uses to kill the app mid-run.
 */
interface FireStepObserver {
    suspend fun beforeStep(step: FireStep)
}
