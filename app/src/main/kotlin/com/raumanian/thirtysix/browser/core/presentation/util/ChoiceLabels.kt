package com.raumanian.thirtysix.browser.core.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
/** The localized name of [mode], shared by the chooser and the Settings row. */
@Composable
fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
        ThemeMode.System -> R.string.settings_theme_system
    },
)

/**
 * The display name of [engine]. Brand names are not translated, so these resources exist in
 * the default locale only (`translatable="false"`).
 */
@Composable
fun searchEngineLabel(engine: SearchEngine): String = stringResource(
    when (engine) {
        SearchEngine.Google -> R.string.search_engine_name_google
        SearchEngine.DuckDuckGo -> R.string.search_engine_name_duckduckgo
        SearchEngine.Bing -> R.string.search_engine_name_bing
    },
)

/**
 * The label for [language]: the localized "Follow system", or the language's endonym, which
 * reads the same in every locale.
 */
@Composable
fun appLanguageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.FollowSystem -> R.string.settings_language_follow_system
        AppLanguage.English -> R.string.language_name_en
        AppLanguage.Vietnamese -> R.string.language_name_vi
        AppLanguage.German -> R.string.language_name_de
        AppLanguage.Russian -> R.string.language_name_ru
        AppLanguage.Korean -> R.string.language_name_ko
        AppLanguage.Japanese -> R.string.language_name_ja
        AppLanguage.Chinese -> R.string.language_name_zh
        AppLanguage.French -> R.string.language_name_fr
    },
)
