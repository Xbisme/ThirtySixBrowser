package com.raumanian.thirtysix.browser.core.data.datasource.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.util.Result
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
 * SettingsRepositoryImpl is the intended consumer; Spec 020's DefaultBrowserInviteRepositoryImpl
 * also uses it for the two invitation keys, which deliberately stay out of `UserSettings`
 * (research R9). No other class should depend on this wrapper directly.
 */
@Singleton
class SettingsDataStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    /** Raw Preferences flow. SettingsRepositoryImpl maps to UserSettings via SettingsMapper. */
    val data: Flow<Preferences> = dataStore.data

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.THEME_MODE] = mode.storageValue
    }

    /** Spec 016 FR-009. */
    suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.DYNAMIC_COLOR_ENABLED] = enabled
    }

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.SEARCH_ENGINE] = engine.storageValue
    }

    /** Spec 016 FR-020 — stored as the window's day count (data-model §1). */
    suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.HISTORY_RETENTION_DAYS] = retention.days
    }

    /** Spec 022 FR-029 — stored as the level's percentage (data-model §1). */
    suspend fun setTextSize(size: TextSize): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.TEXT_ZOOM_PERCENT] = size.percent
    }

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.IS_ONBOARDING_COMPLETED] = value
    }

    /** Spec 020 FR-025 / FR-026 — only ever writes `true` (data-model INV-8). */
    suspend fun markDefaultBrowserInviteDismissed(): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.DEFAULT_BROWSER_INVITE_DISMISSED] = true
    }

    /** Spec 024 FR-007 — the interrupted-Fire marker (data-model "DataStore — one key"). */
    suspend fun setFireInProgress(value: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.FIRE_IN_PROGRESS] = value
    }

    /** Spec 020 FR-024a — adds one launch, never past [cap]. Read and write in one transaction. */
    suspend fun incrementAppLaunchCount(cap: Int): Result<Unit> = editCatching { prefs ->
        val current = prefs[StorageKeys.APP_LAUNCH_COUNT] ?: 0
        if (current < cap) prefs[StorageKeys.APP_LAUNCH_COUNT] = current + 1
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
