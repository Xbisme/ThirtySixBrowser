@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.presentation.util.appLanguageLabel
// Each language is listed under its own name, so someone who cannot read the current language can find theirs.
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

const val TEST_TAG_SETTINGS_LANGUAGE_DIALOG: String = "settings_language_dialog"
