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
 * Typed setters over the settings DataStore. Only IOException is caught, so cancellation propagates.
 * Used by the settings repository and the default-browser invitation, whose keys stay out of `UserSettings`.
 */
@Singleton
@Suppress("TooManyFunctions") // One typed setter per stored preference.
class SettingsDataStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val data: Flow<Preferences> = dataStore.data

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.THEME_MODE] = mode.storageValue
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.DYNAMIC_COLOR_ENABLED] = enabled
    }

    suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.SEARCH_ENGINE] = engine.storageValue
    }

    suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.HISTORY_RETENTION_DAYS] = retention.days
    }

    suspend fun setTextSize(size: TextSize): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.TEXT_ZOOM_PERCENT] = size.percent
    }

    suspend fun setSwipeNavigationEnabled(enabled: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.SWIPE_NAVIGATION_ENABLED] = enabled
    }

    suspend fun setTabSwipeEnabled(enabled: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.TAB_SWIPE_ENABLED] = enabled
    }

    suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.IS_ONBOARDING_COMPLETED] = value
    }

    /** Only ever writes `true`: a dismissed invitation never returns. */
    suspend fun markDefaultBrowserInviteDismissed(): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.DEFAULT_BROWSER_INVITE_DISMISSED] = true
    }

    suspend fun setFireInProgress(value: Boolean): Result<Unit> = editCatching { prefs ->
        prefs[StorageKeys.FIRE_IN_PROGRESS] = value
    }

    /** Adds one launch, never past [cap]; read and write share one transaction. */
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
