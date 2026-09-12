@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.AppLanguage

/**
 * Spec 016 US3 — choose Follow system or one of the eight app languages.
 *
 * Nine options in [AppLanguage] order: the localized "Follow system" first, then each language
 * under its own name, so a user who cannot read the current language can still find theirs
 * (FR-014). Public and stateless for Spec 018's onboarding (FR-037, research.md R14).
 */
@Composable
fun AppLanguageChooserDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceDialog(
        title = stringResource(R.string.settings_language_title),
        options = AppLanguage.entries,
        selected = selected,
        optionLabel = { appLanguageLabel(it) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier.testTag(TEST_TAG_SETTINGS_LANGUAGE_DIALOG),
    )
}

/**
 * The label for [language]: the localized "Follow system", or the language's endonym, which
 * reads the same in every locale (research R11).
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

const val TEST_TAG_SETTINGS_LANGUAGE_DIALOG: String = "settings_language_dialog"
