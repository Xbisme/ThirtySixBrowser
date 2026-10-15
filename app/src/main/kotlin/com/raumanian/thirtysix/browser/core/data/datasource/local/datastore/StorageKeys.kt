package com.raumanian.thirtysix.browser.core.data.datasource.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Settings keys. A key that has shipped in a release never changes name, meaning or value type:
 * add a new key, migrate once, and keep the old one `@Deprecated` for a release before deleting it.
 */
object StorageKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")

    val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")

    val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")

    /** Also set once the app is seen as the default browser; never written back to `false`. */
    val DEFAULT_BROWSER_INVITE_DISMISSED = booleanPreferencesKey("default_browser_invite_dismissed")

    /** Counts launches only up to `BrowserLimits.DEFAULT_BROWSER_INVITE_MIN_LAUNCHES`. */
    val APP_LAUNCH_COUNT = intPreferencesKey("app_launch_count")

    val TEXT_ZOOM_PERCENT = intPreferencesKey("text_zoom_percent")

    /** Set across a Fire run; still `true` at start-up means the run must finish before any tab shows. */
    val FIRE_IN_PROGRESS = booleanPreferencesKey("fire_in_progress")
}
