// CONTRACT — Spec 006 datastore-settings
// Package destination: com.raumanian.thirtysix.browser.domain.usecase
//
// Single-method observer use case. Operator-invocable so call sites read as
// observeUserSettings() instead of observeUserSettings.invoke(). Pure delegation
// to repository — exists to satisfy Constitution §IV "all business logic in
// domain/usecase/" rule and to provide a stable consumer API surface.

package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUserSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<UserSettings> = repository.observeSettings()
}
