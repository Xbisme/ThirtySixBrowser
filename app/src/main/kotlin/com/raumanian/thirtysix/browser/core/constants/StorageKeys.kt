package com.raumanian.thirtysix.browser.core.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore Preferences keys — single source of truth (Spec 006 FR-017).
 *
 * SCHEMA EVOLUTION RULES (Spec 006 FR-019):
 *
 * 1. Once a key in this file has shipped to a user via a release build, its NAME
 *    MUST NOT be reused for a different meaning.
 * 2. Once a key in this file has shipped to a user, its stored VALUE TYPE MUST NOT
 *    change (e.g., String → Int requires a new key).
 * 3. To change semantics: introduce a NEW key, write a one-time migration that
 *    reads the old key, transforms, writes the new key, and clears the old key.
 *    The old key declaration MUST stay in this file marked @Deprecated until at
 *    least one full release cycle has passed.
 * 4. To remove a no-longer-used key: mark @Deprecated for one release cycle, then
 *    delete in the following release.
 *
 * The negative-path test SettingsMapperTest#renamedKey_returnsDefault_oldValueGone
 * codifies rule 1 by simulating a rename and asserting the documented "default
 * returned, old value not silently inherited" behavior.
 *
 * Spec 016 deleted `language_override` outright rather than deprecating it. Rules 1–4 bind
 * keys that have shipped to a user in a release build, and this one never did — the app
 * is unreleased and nothing ever read the value (specs/016-settings-screen/research.md R4).
 */
object StorageKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")

    /** Spec 016 FR-009 — whether Material dynamic color is applied on Android 12+. */
    val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")

    /** Spec 016 FR-020 — the history retention window, stored as its day count. */
    val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
}
