package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Reads the settings once, for the start-up routing decision: observing would first emit
 * `UserSettings.DEFAULT` (onboarding not completed), and `NavHost` fixes its start at first composition.
 */
class GetUserSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(): UserSettings = repository.currentSettings()
}
