// Spec 016 contract — AMENDED SettingsRepository (originally Spec 006 FR-015).
// Target: app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/SettingsRepository.kt
//
// Changes versus Spec 006:
//   + setDynamicColorEnabled   (FR-009)
//   + setHistoryRetention      (FR-020)
//   - setLanguageOverride      (FR-017, research R4) — the platform owns the app language;
//                                see AppLanguageController.kt. The LanguageOverride model,
//                                its DataStore key, mapper branch and use case go with it.

package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Persistence-backed user settings.
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
 * would go stale the moment the user changed it from Android 13+ system settings (FR-016).
 */
interface SettingsRepository {

    fun observeSettings(): Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>

    /**
     * FR-009. Persisted on every Android version; consulted only on Android 12+ (FR-010).
     * The capability check belongs to the presentation layer, not here.
     */
    suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit>

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit>

    /**
     * FR-020. Stores the window's day count. Writes the value only — pruning is the
     * caller's concern (`ChangeHistoryRetentionUseCase`), because a repository must not
     * depend on `HistoryRepository`.
     */
    suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit>

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit>
}
