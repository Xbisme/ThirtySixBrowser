// CONTRACT — Spec 006 datastore-settings
// Package destination: com.raumanian.thirtysix.browser.data.local.datastore
//
// Data-layer wrapper around the Hilt-provided DataStore<Preferences> singleton.
// Thin: only translates type-safe domain calls into Preferences read/edit calls.
// All write paths catch IOException and surface Result<Unit>.

package com.raumanian.thirtysix.browser.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings DataStore wrapper.
 *
 * Receives a Hilt-provided [DataStore] from SettingsModule. Exposes the raw
 * Preferences flow for the Repository to map, plus 4 typed setters that
 * return Result<Unit> (Spec 006 Q1).
 *
 * Lives in data/local/datastore/ — this IS the data source. SettingsRepositoryImpl
 * consumes it; no other code may.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Raw Preferences flow. Repository maps to UserSettings via SettingsMapper. */
    val data: Flow<Preferences> = dataStore.data

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = editCatching { prefs ->
        prefs[com.raumanian.thirtysix.browser.core.constants.StorageKeys.THEME_MODE] =
            mode.storageValue
    }

    suspend fun setLanguageOverride(override: LanguageOverride): Result<Unit> =
        editCatching { prefs ->
            val key = com.raumanian.thirtysix.browser.core.constants.StorageKeys.LANGUAGE_OVERRIDE
            when (override) {
                LanguageOverride.FollowSystem -> prefs.remove(key)
                is LanguageOverride.Explicit -> prefs[key] = override.bcp47
            }
        }

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = editCatching { prefs ->
        prefs[com.raumanian.thirtysix.browser.core.constants.StorageKeys.SEARCH_ENGINE] =
            engine.storageValue
    }

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[com.raumanian.thirtysix.browser.core.constants.StorageKeys.IS_ONBOARDING_COMPLETED] =
            value
    }

    /**
     * Internal helper: run [block] inside dataStore.edit, catch IOException only,
     * map via AppError.from(). CancellationException is intentionally NOT caught.
     */
    private suspend fun editCatching(
        block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): Result<Unit> = try {
        dataStore.edit(block)
        Result.Success(Unit)
    } catch (io: java.io.IOException) {
        Result.Error(com.raumanian.thirtysix.browser.core.error.AppError.from(io))
    }
}
