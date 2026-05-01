package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import javax.inject.Inject

class SetLanguageOverrideUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(override: LanguageOverride): Result<Unit> =
        repository.setLanguageOverride(override)
}
