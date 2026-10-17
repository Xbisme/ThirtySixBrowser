package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Setters report a failed write as `Result.Error`, never by throwing. There is no language accessor:
 * the platform owns the app language, and a copy here would go stale.
 */
interface SettingsRepository {

    fun observeSettings(): Flow<UserSettings>

    /** Suspends for a real value, never the default; start-up routing reads it before the nav graph exists. */
    suspend fun currentSettings(): UserSettings

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>

    /** Persisted on every version; only Android 12+ consults it. */
    suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit>

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit>

    /** Writes the value only; pruning belongs to the caller, as a repository cannot depend on another. */
    suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit>

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit>

    suspend fun setTextSize(size: TextSize): Result<Unit>

    suspend fun setSwipeNavigationEnabled(enabled: Boolean): Result<Unit>

    suspend fun setTabSwipeEnabled(enabled: Boolean): Result<Unit>
}
