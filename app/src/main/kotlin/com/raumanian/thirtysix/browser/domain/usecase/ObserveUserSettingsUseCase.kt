package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observe the current user-settings snapshot (Spec 006).
 *
 * Operator-invocable so call sites read as `observeUserSettings()` rather than
 * `observeUserSettings.invoke()`. Pure delegation to repository — exists per
 * Constitution §IV "all business logic in domain/usecase/" so the consumer-
 * facing API is stable as Spec 016 / Spec 018 add UI consumers.
 */
class ObserveUserSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<UserSettings> = repository.observeSettings()
}
