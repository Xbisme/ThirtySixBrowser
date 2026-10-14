@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/**
 * Spec 015 · Spec 019 FR-041 — top of the Downloads screen: a back button, then the large
 * screen title (`headlineSmall`, design system "large screen titles"). Consumes the status-bar
 * inset itself so the screen can draw edge to edge.
 *
 * Unlike History there is no clear-all action: bulk clearing of downloads is out of scope.
 */
@Composable
fun DownloadsTopBar(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(vertical = Spacing.xs),
    ) {
        RoundIconButton(
            icon = AppIcons.ArrowBack,
            contentDescription = stringResource(R.string.downloads_action_back),
            onClick = onBackClick,
            style = RoundIconStyle.Bare,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.downloads_screen_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .semantics { heading() }
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}
