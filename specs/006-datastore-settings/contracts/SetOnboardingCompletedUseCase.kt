// CONTRACT — Spec 006 datastore-settings
// Package destination: com.raumanian.thirtysix.browser.domain.usecase

package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import javax.inject.Inject

class SetOnboardingCompletedUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(value: Boolean): Result<Unit> =
        repository.setOnboardingCompleted(value)
}
