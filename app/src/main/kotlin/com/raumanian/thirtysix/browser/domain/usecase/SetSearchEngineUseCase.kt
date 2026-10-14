package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject

class SetSearchEngineUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(engine: SearchEngine): Result<Unit> =
        repository.setSearchEngine(engine)
}
