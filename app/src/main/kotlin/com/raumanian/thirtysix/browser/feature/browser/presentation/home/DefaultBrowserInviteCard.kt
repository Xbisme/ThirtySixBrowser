@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/**
 * Spec 020 contracts C3.5 — the Home invitation to make ThirtySix the default browser.
 *
 * Built from the design system's banner idiom (the hub's row banner: `surfaceContainer`, extra
 * large corners, a display-face title) because the owner's design for it is still to come
 * (spec Assumptions, design waiver). Static content: nothing here reads frame-rate state (§V).
 *
 * The action does NOT dismiss the card (FR-025, FR-027); only [onDismiss] does.
 */
@Composable
fun DefaultBrowserInviteCard(onAction: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.surfaceContainer)
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.md)
            .testTag(TEST_TAG_HOME_DEFAULT_BROWSER_INVITE),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_default_browser_invite_title),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                ),
                color = scheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            RoundIconButton(
                icon = AppIcons.Close,
                contentDescription = stringResource(R.string.home_default_browser_invite_dismiss),
                onClick = onDismiss,
                style = RoundIconStyle.Bare,
                modifier = Modifier.testTag(TEST_TAG_HOME_DEFAULT_BROWSER_INVITE_DISMISS),
            )
        }
        Text(
            text = stringResource(R.string.home_default_browser_invite_body),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.md),
        )
        PillButton(
            label = stringResource(R.string.home_default_browser_invite_action),
            onClick = onAction,
            style = PillStyle.Filled,
            modifier = Modifier.testTag(TEST_TAG_HOME_DEFAULT_BROWSER_INVITE_ACTION),
        )
    }
}

const val TEST_TAG_HOME_DEFAULT_BROWSER_INVITE: String = "home_default_browser_invite"
const val TEST_TAG_HOME_DEFAULT_BROWSER_INVITE_ACTION: String = "home_default_browser_invite_action"
const val TEST_TAG_HOME_DEFAULT_BROWSER_INVITE_DISMISS: String = "home_default_browser_invite_dismiss"
