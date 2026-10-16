package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared in-memory [SettingsRepository]. Each setter records its name in [writes] and in an
 * optional shared [callLog]; [failNextWrite] makes exactly one following write return
 * `Result.Error(IOException)` without changing state.
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

    /** Returns the same snapshot [observeSettings] would emit. */
    override suspend fun currentSettings(): UserSettings = state.value

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

    override suspend fun setTextSize(size: TextSize): Result<Unit> =
        write(CALL_SET_TEXT_SIZE) { copy(textSize = size) }

    override suspend fun setSwipeNavigationEnabled(enabled: Boolean): Result<Unit> =
        write(CALL_SET_SWIPE_NAVIGATION) { copy(isSwipeNavigationEnabled = enabled) }

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
        const val CALL_SET_TEXT_SIZE: String = "settings.setTextSize"
        const val CALL_SET_SWIPE_NAVIGATION: String = "settings.setSwipeNavigationEnabled"
    }
}
