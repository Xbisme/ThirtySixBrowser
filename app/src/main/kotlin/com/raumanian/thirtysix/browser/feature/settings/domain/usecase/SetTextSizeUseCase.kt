package com.raumanian.thirtysix.browser.feature.settings.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

/** The text size is app-wide: every open tab picks it up. */
class SetTextSizeUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(size: TextSize): Result<Unit> = repository.setTextSize(size)
}
