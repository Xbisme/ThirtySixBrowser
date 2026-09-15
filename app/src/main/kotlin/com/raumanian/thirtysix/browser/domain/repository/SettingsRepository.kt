package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Persistence-backed user settings (Spec 006 FR-015, amended by Spec 016).
 *
 * Unchanged conventions from Spec 006:
 *  - Setters return `Result<Unit>`; a disk-write failure is `Result.Error(IOException)`,
 *    never a thrown exception. `CancellationException` is not caught.
 *  - [observeSettings] emits one coherent [UserSettings] snapshot whenever any key changes,
 *    with `distinctUntilChanged` applied.
 *  - The implementation depends only on `SettingsDataStore` and `SettingsMapper` — never on
 *    another repository and never on a platform service (Constitution §IV).
 *
 * Deliberately absent: any language accessor. A second, app-owned copy of the language
 * would go stale the moment the user changed it from Android 13+ system settings (FR-016);
 * the platform seam `AppLanguageController` owns it instead.
 */
interface SettingsRepository {

    fun observeSettings(): Flow<UserSettings>

    /**
     * Spec 018 — the current settings, read once.
     *
     * Suspends until a real value is available and never returns [UserSettings.DEFAULT] as a
     * placeholder. Added because the start-up decision has to know whether onboarding is
     * complete BEFORE the navigation graph is built.
     *
     * Why [observeSettings] cannot serve that: collected with
     * `collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)`, the first emission
     * carries `isOnboardingCompleted = false` (the documented default). `NavHost` captures its
     * `startDestination` at first composition, so routing on that first emission sends EVERY
     * launch to onboarding — not as a one-frame flash, but as the graph's actual start route
     * (Spec 018 research.md R2).
     *
     * Callers that need to follow changes keep using [observeSettings]; this is only for
     * deciding something before any UI exists.
     */
    suspend fun currentSettings(): UserSettings

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>

    /**
     * Spec 016 FR-009. Persisted on every Android version; consulted only on Android 12+
     * (FR-010). The capability check belongs to the presentation layer, not here.
     */
    suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit>

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit>

    /**
     * Spec 016 FR-020. Stores the window's day count. Writes the value only — pruning is the
     * caller's concern (`ChangeHistoryRetentionUseCase`), because a repository must not depend
     * on `HistoryRepository`.
     */
    suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit>

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit>
}
