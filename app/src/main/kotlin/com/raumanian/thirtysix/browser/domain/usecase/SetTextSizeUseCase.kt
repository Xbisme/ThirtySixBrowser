package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.domain.model.TextSize
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import javax.inject.Inject

/** Spec 022 FR-029 / FR-031 — saves the app-wide text size; every open tab picks it up. */
class SetTextSizeUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(size: TextSize): Result<Unit> = repository.setTextSize(size)
}
