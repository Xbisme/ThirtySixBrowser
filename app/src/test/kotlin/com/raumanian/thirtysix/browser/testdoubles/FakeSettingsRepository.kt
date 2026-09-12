package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spec 016 T019 — shared in-memory [SettingsRepository] for view-model and use-case tests.
 *
 * Backed by a hot [MutableStateFlow] so observers see every accepted write. Each setter
 * records its name in [writes] — and in [callLog], when a test shares one log across several
 * fakes to assert ordering. [failNextWrite] makes exactly one following write return
 * `Result.Error(IOException)` without changing state, for FR-043 tests.
 */
class FakeSettingsRepository(
    initial: UserSettings = UserSettings.DEFAULT,
    private val callLog: MutableList<String> = mutableListOf(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    /** The latest accepted snapshot. */
    val current: UserSettings get() = state.value

    /** Names of every setter call, including failed ones, in call order. */
    val writes: MutableList<String> = mutableListOf()

    var failNextWrite: Boolean = false

    override fun observeSettings(): Flow<UserSettings> = state.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> =
        write(CALL_SET_THEME_MODE) { copy(themeMode = mode) }

    override suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> =
        write(CALL_SET_DYNAMIC_COLOR) { copy(isDynamicColorEnabled = enabled) }

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> =
        write(CALL_SET_SEARCH_ENGINE) { copy(searchEngine = engine) }

    override suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> =
        write(CALL_SET_HISTORY_RETENTION) { copy(historyRetention = retention) }

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> =
        write(CALL_SET_ONBOARDING_COMPLETED) { copy(isOnboardingCompleted = value) }

    private fun write(name: String, transform: UserSettings.() -> UserSettings): Result<Unit> {
        writes += name
        callLog += name
        if (failNextWrite) {
            failNextWrite = false
            return Result.Error(IOException("simulated settings write failure"))
        }
        state.value = state.value.transform()
        return Result.Success(Unit)
    }

    companion object {
        const val CALL_SET_THEME_MODE: String = "settings.setThemeMode"
        const val CALL_SET_DYNAMIC_COLOR: String = "settings.setDynamicColorEnabled"
        const val CALL_SET_SEARCH_ENGINE: String = "settings.setSearchEngine"
        const val CALL_SET_HISTORY_RETENTION: String = "settings.setHistoryRetention"
        const val CALL_SET_ONBOARDING_COMPLETED: String = "settings.setOnboardingCompleted"
    }
}
