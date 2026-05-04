@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R

/**
 * Spec 012 (T052 / FR-015) — small persistent indicator displayed beside the
 * address bar when the active tab is incognito.
 *
 * Visual treatment uses [MaterialTheme.colorScheme.tertiaryContainer] for the
 * background circle (same token as the incognito new-tab card's leading
 * glyph) so the user sees consistent "incognito ⇒ tertiary container"
 * mapping across the switcher and address bar.
 *
 * a11y: carries the localized `browser_a11y_incognito_indicator`
 * contentDescription so TalkBack announces incognito mode (FR-025).
 */
@Composable
fun IncognitoIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(INDICATOR_SIZE_DP)
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
            )
            .testTag(TEST_TAG_INCOGNITO_INDICATOR),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = stringResource(R.string.browser_a11y_incognito_indicator),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(INDICATOR_INNER_SIZE_DP),
        )
    }
}

const val TEST_TAG_INCOGNITO_INDICATOR: String = "browser_incognito_indicator"

private val INDICATOR_SIZE_DP = 24.dp
private val INDICATOR_INNER_SIZE_DP = 16.dp
