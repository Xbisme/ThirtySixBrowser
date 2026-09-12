@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.ThemeMode

/**
 * Spec 016 US1 — choose Light, Dark or follow the system.
 *
 * Public and stateless on purpose: it knows nothing about any view model and reports the
 * choice through [onSelect], so Spec 018's onboarding can reuse it unchanged (FR-037,
 * research.md R14).
 */
@Composable
fun ThemeModeChooserDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceDialog(
        title = stringResource(R.string.settings_theme_title),
        options = ThemeMode.entries,
        selected = selected,
        optionLabel = { themeModeLabel(it) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier.testTag(TEST_TAG_SETTINGS_THEME_DIALOG),
    )
}

/** The localized name of [mode], shared by the chooser and the Settings row (FR-003). */
@Composable
fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
        ThemeMode.System -> R.string.settings_theme_system
    },
)

const val TEST_TAG_SETTINGS_THEME_DIALOG: String = "settings_theme_dialog"
