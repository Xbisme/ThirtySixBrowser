package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings use cases are pure delegations to [SettingsRepository]. The app language goes
 * through `AppLanguageController` instead, covered by `AppLanguageUseCasesTest`.
 */
class SettingsUseCasesTest {

    private val fake = FakeSettingsRepository()

    @Test
    fun observeUserSettingsUseCase_delegatesToRepository() {
        val useCase = ObserveUserSettingsUseCase(fake)
        assertEquals(fake.observeFlow, useCase())
    }

    @Test
    fun setThemeModeUseCase_delegates() = runBlocking {
        val useCase = SetThemeModeUseCase(fake)
        val result = useCase(ThemeMode.Dark)
        assertTrue(result is Result.Success)
        assertEquals(ThemeMode.Dark, fake.lastThemeMode)
    }

    @Test
    fun setSearchEngineUseCase_delegates() = runBlocking {
        val useCase = SetSearchEngineUseCase(fake)
        val result = useCase(SearchEngine.Google)
        assertTrue(result is Result.Success)
        assertEquals(SearchEngine.Google, fake.lastSearchEngine)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    val observeFlow: Flow<UserSettings> = flowOf(UserSettings.DEFAULT)

    var lastThemeMode: ThemeMode? = null
    var lastDynamicColorEnabled: Boolean? = null
    var lastSearchEngine: SearchEngine? = null
    var lastHistoryRetention: HistoryRetention? = null
    var lastOnboardingCompleted: Boolean? = null

    override fun observeSettings(): Flow<UserSettings> = observeFlow

    // Only exercised through the observer, so the defaults are the honest answer here.

    override suspend fun currentSettings(): UserSettings = UserSettings.DEFAULT

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> {
        lastThemeMode = mode
        return Result.Success(Unit)
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> {
        lastDynamicColorEnabled = enabled
        return Result.Success(Unit)
    }

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> {
        lastSearchEngine = engine
        return Result.Success(Unit)
    }

    override suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> {
        lastHistoryRetention = retention
        return Result.Success(Unit)
    }

    override suspend fun setTextSize(size: com.raumanian.thirtysix.browser.core.domain.model.TextSize) =

        com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> {
        lastOnboardingCompleted = value
        return Result.Success(Unit)
    }
}
