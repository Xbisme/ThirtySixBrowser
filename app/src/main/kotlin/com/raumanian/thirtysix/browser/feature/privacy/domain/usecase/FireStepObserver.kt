package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.FireStep
/** Called before each Fire step; does nothing unless a debug-only receiver has set a delay. */
interface FireStepObserver {
    suspend fun beforeStep(step: FireStep)
}
