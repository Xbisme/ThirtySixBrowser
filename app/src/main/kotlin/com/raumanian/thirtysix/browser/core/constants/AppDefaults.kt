package com.raumanian.thirtysix.browser.core.constants

import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode

/**
 * Application-wide default values for user-overridable settings (Spec 006 FR-018).
 *
 * Per Constitution §III No-Hardcode Rule: defaults live HERE, not inline at call
 * sites. Both [com.raumanian.thirtysix.browser.domain.model.UserSettings.DEFAULT]
 * and [com.raumanian.thirtysix.browser.data.mapper.SettingsMapper] read from
 * this object so a single edit propagates everywhere.
 */
object AppDefaults {
    val THEME_MODE: ThemeMode = ThemeMode.System
    val LANGUAGE_OVERRIDE: LanguageOverride = LanguageOverride.FollowSystem
    val SEARCH_ENGINE: SearchEngine = SearchEngine.Google
    const val IS_ONBOARDING_COMPLETED: Boolean = false
}
