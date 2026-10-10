@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.components.RoundIconButton
import com.raumanian.thirtysix.browser.presentation.components.RoundIconStyle
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 016 FR-002 / Spec 019 FR-044 — the back affordance that returns to the browser, over the
 * large "Settings" title. The bar pads itself below the status bar, since the app draws
 * edge to edge.
 */
@Composable
fun SettingsTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Spacing.sm, bottom = Spacing.sm),
    ) {
        RoundIconButton(
            icon = AppIcons.ArrowBack,
            contentDescription = stringResource(R.string.settings_action_back),
            onClick = onBackClick,
            style = RoundIconStyle.Bare,
            modifier = Modifier
                .padding(start = Spacing.xs)
                .testTag(TEST_TAG_SETTINGS_BACK),
        )
        Text(
            text = stringResource(R.string.settings_screen_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .semantics { heading() },
        )
    }
}

const val TEST_TAG_SETTINGS_BACK: String = "settings_back"
