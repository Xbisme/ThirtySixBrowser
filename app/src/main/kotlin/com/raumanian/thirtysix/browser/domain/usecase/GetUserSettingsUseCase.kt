package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Read the current user-settings snapshot once (Spec 018).
 *
 * The one-shot counterpart to [ObserveUserSettingsUseCase]. Operator-invocable so call sites
 * read as `getUserSettings()`. Pure delegation to the repository, per Constitution §IV.
 *
 * Exists for one caller: the start-up decision in `MainActivity`, which must know whether
 * onboarding is complete BEFORE the navigation graph is built. Observing cannot answer that —
 * the first emission is `UserSettings.DEFAULT`, whose `isOnboardingCompleted` is `false`, and
 * `NavHost` captures its start destination at first composition (Spec 018 research.md R2).
 *
 * Anything that needs to react to later changes still uses [ObserveUserSettingsUseCase].
 */
class GetUserSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(): UserSettings = repository.currentSettings()
}
