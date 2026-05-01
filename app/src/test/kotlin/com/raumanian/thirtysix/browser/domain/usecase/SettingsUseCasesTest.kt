package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the 5 use cases are pure delegations to [SettingsRepository] (Spec 006).
 * Uses a hand-rolled FakeSettingsRepository (no MockK in classpath).
 */
class SettingsUseCasesTest {

    private val fake = FakeSettingsRepository()

    @Test
    fun observeUserSettingsUseCase_delegatesToRepository() {
        val useCase = ObserveUserSettingsUseCase(fake)
        // The flow comes through unchanged
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
    fun setLanguageOverrideUseCase_delegates() = runBlocking {
        val useCase = SetLanguageOverrideUseCase(fake)
        val result = useCase(LanguageOverride.Explicit("vi"))
        assertTrue(result is Result.Success)
        assertEquals(LanguageOverride.Explicit("vi"), fake.lastLanguageOverride)
    }

    @Test
    fun setSearchEngineUseCase_delegates() = runBlocking {
        val useCase = SetSearchEngineUseCase(fake)
        val result = useCase(SearchEngine.Google)
        assertTrue(result is Result.Success)
        assertEquals(SearchEngine.Google, fake.lastSearchEngine)
    }

    @Test
    fun setOnboardingCompletedUseCase_delegates() = runBlocking {
        val useCase = SetOnboardingCompletedUseCase(fake)
        val result = useCase(true)
        assertTrue(result is Result.Success)
        assertEquals(true, fake.lastOnboardingCompleted)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    val observeFlow: Flow<UserSettings> = flowOf(UserSettings.DEFAULT)

    var lastThemeMode: ThemeMode? = null
    var lastLanguageOverride: LanguageOverride? = null
    var lastSearchEngine: SearchEngine? = null
    var lastOnboardingCompleted: Boolean? = null

    override fun observeSettings(): Flow<UserSettings> = observeFlow

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> {
        lastThemeMode = mode
        return Result.Success(Unit)
    }

    override suspend fun setLanguageOverride(override: LanguageOverride): Result<Unit> {
        lastLanguageOverride = override
        return Result.Success(Unit)
    }

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> {
        lastSearchEngine = engine
        return Result.Success(Unit)
    }

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> {
        lastOnboardingCompleted = value
        return Result.Success(Unit)
    }
}
