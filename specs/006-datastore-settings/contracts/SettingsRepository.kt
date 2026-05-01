// CONTRACT — Spec 006 datastore-settings
// Package destination: com.raumanian.thirtysix.browser.domain.repository
//
// Domain-layer interface. ZERO Android imports allowed.

package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Persistence-backed user settings.
 *
 * Per Constitution §IV (Repository pattern): consumers (ViewModels, Activities)
 * interact via this interface, never the underlying DataStore.
 *
 * Per Spec 006 Q1 clarification: setters return Result<Unit> from the existing
 * core/result/ wrapper. IOException maps to AppError.Database via AppError.from().
 * Setters do NOT throw for routine disk failures.
 *
 * Per FR-010: observeSettings emits a coherent snapshot every time any individual
 * key changes; consumers branch on UserSettings fields, not on individual flows.
 */
interface SettingsRepository {

    /**
     * Observe the full user-settings snapshot.
     *
     * - First emission may be the documented defaults (if disk file does not exist
     *   yet OR while the file is being read on cold start).
     * - distinctUntilChanged is applied at the impl boundary so consumers do not
     *   see redundant emissions when an unrelated process notifies the underlying
     *   DataStore.
     */
    fun observeSettings(): Flow<UserSettings>

    /**
     * Persist a new theme mode.
     *
     * Returns Result.Success(Unit) when the value reaches disk; Result.Error(
     * AppError.Database) on IOException.
     */
    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>

    /**
     * Persist a new language override (or clear it via [LanguageOverride.FollowSystem]).
     */
    suspend fun setLanguageOverride(override: LanguageOverride): Result<Unit>

    /**
     * Persist a new search engine choice.
     */
    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit>

    /**
     * Mark the onboarding flow as completed (or reset).
     */
    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit>
}
