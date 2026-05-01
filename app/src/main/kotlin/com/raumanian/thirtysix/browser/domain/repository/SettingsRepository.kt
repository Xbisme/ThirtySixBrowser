package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Persistence-backed user settings (Spec 006 FR-015).
 *
 * Per Constitution §IV (Repository pattern): consumers (ViewModels, Activities)
 * interact via this interface, never the underlying DataStore.
 *
 * Per Spec 006 Q1 clarification: setters return `Result<Unit>` from the existing
 * `core/result/` wrapper. On disk-write failure they return `Result.Error(throwable)`
 * carrying the underlying `IOException`; consumers may map to `AppError` via
 * `AppError.from(throwable)` if typed error handling is needed.
 *
 * Per FR-010: observeSettings emits a coherent snapshot every time any individual
 * key changes; consumers branch on UserSettings fields, not on per-key flows.
 * The implementation applies distinctUntilChanged so duplicate snapshots collapse.
 */
interface SettingsRepository {

    fun observeSettings(): Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>

    suspend fun setLanguageOverride(override: LanguageOverride): Result<Unit>

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit>

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit>
}
