package com.raumanian.thirtysix.browser.feature.settings.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.settings.data.mapper.SettingsMapper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    // Same source as observeSettings, so the two never disagree; first() waits for the disk read.
    override suspend fun currentSettings(): UserSettings = mapper.toDomain(dataStore.data.first())

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = dataStore.setThemeMode(mode)

    override suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> =
        dataStore.setDynamicColorEnabled(enabled)

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = dataStore.setSearchEngine(engine)

    override suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> =
        dataStore.setHistoryRetention(retention)

    override suspend fun setTextSize(size: TextSize): Result<Unit> = dataStore.setTextSize(size)

    override suspend fun setSwipeNavigationEnabled(enabled: Boolean): Result<Unit> =
        dataStore.setSwipeNavigationEnabled(enabled)

    override suspend fun setTabSwipeEnabled(enabled: Boolean): Result<Unit> = dataStore.setTabSwipeEnabled(enabled)

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> =
        dataStore.setOnboardingCompleted(value)
}
