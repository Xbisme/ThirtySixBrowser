package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.data.mapper.SettingsMapper
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val mapper: SettingsMapper,
) : SettingsRepository {
    override fun observeSettings(): Flow<UserSettings> =
        dataStore.data
            .map(mapper::toDomain)
            .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = dataStore.setThemeMode(mode)

    override suspend fun setLanguageOverride(override: LanguageOverride): Result<Unit> =
        dataStore.setLanguageOverride(override)

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = dataStore.setSearchEngine(engine)

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> =
        dataStore.setOnboardingCompleted(value)
}
