@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.browser.ErrorReason
import com.raumanian.thirtysix.browser.presentation.browser.toUserMessageRes
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 007 US3 — full-screen error UI shown when [ErrorReason] is non-null.
 *
 * Replaces (does NOT overlay) the WebView surface. All colors / typography /
 * spacing flow through theme tokens (Constitution §III).
 */
@Composable
fun BrowserErrorState(
    reason: ErrorReason,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .testTag(TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null, // decorative; the title text below conveys meaning.
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = stringResource(R.string.browser_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = stringResource(reason.toUserMessageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

const val TEST_TAG_BROWSER_ERROR_STATE: String = "browser_error_state"

private const val TEST_TAG: String = TEST_TAG_BROWSER_ERROR_STATE
private val ICON_SIZE = 64.dp
