package com.raumanian.thirtysix.browser.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Settings DataStore wrapper (Spec 006).
 *
 * Wraps the Hilt-provided `DataStore<Preferences>` singleton with type-safe
 * setters. Each setter catches IOException only — CancellationException is
 * intentionally NOT caught so coroutine cancellation propagates correctly
 * (same convention as Spec 002 BaseViewModel.launchSafely).
 *
 * Setters return `Result<Unit>` (Q1 clarification): `Success(Unit)` on persisted
 * write; `Error(throwable)` on disk failure. The `Result.Error` carries the raw
 * IOException; consumers needing typed error mapping call `AppError.from(...)`.
 *
 * SettingsRepositoryImpl is the only intended consumer; no other class should
 * depend on this wrapper directly.
 */
@Singleton
class SettingsDataStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    /** Raw Preferences flow. SettingsRepositoryImpl maps to UserSettings via SettingsMapper. */
    val data: Flow<Preferences> = dataStore.data

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.THEME_MODE] = mode.storageValue
    }

    suspend fun setLanguageOverride(override: LanguageOverride): Result<Unit> =
        editCatching { prefs ->
            when (override) {
                LanguageOverride.FollowSystem -> prefs.remove(StorageKeys.LANGUAGE_OVERRIDE)
                is LanguageOverride.Explicit -> prefs[StorageKeys.LANGUAGE_OVERRIDE] = override.bcp47
            }
        }

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.SEARCH_ENGINE] = engine.storageValue
    }

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.IS_ONBOARDING_COMPLETED] = value
    }

    private suspend fun editCatching(
        block: suspend (MutablePreferences) -> Unit,
    ): Result<Unit> = try {
        dataStore.edit(block)
        Result.Success(Unit)
    } catch (io: IOException) {
        Result.Error(io)
    }
}
