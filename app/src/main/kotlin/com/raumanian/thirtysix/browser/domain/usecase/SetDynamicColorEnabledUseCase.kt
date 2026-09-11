package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Spec 016 FR-009 — persist whether Material dynamic color is used. Pure delegation, like the
 * other Spec 006 setters. Whether the device can show dynamic color at all is a presentation
 * concern (FR-010).
 */
class SetDynamicColorEnabledUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> = repository.setDynamicColorEnabled(enabled)
}
