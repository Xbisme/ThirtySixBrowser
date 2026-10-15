package com.raumanian.thirtysix.browser.feature.settings.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

class SetDynamicColorEnabledUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> = repository.setDynamicColorEnabled(enabled)
}
