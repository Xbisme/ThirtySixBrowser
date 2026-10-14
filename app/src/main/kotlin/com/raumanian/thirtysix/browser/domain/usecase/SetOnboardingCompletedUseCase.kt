package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

class SetOnboardingCompletedUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(value: Boolean): Result<Unit> =
        repository.setOnboardingCompleted(value)
}
