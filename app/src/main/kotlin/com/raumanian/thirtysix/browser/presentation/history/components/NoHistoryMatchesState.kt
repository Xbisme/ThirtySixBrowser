@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

/**
 * Spec 014 FR-015 — rendered in place of the list when a threshold-meeting search
 * query matches zero entries. Distinct from [EmptyHistoryState], which means "there
 * is no history at all"; this one means "history exists, but nothing matches".
 * Drawn like [EmptyHistoryState], with a neutral search coin (Spec 019).
 */
@Composable
fun NoHistoryMatchesState(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TEST_TAG_HISTORY_NO_MATCHES)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.tileWell)
                .background(MaterialTheme.colorScheme.surfaceContainer, ThirtySixTheme.shapes.full),
        ) {
            Icon(
                painter = painterResource(AppIcons.Search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconLarge),
            )
        }
        Text(
            text = stringResource(R.string.history_no_matches_title, query),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.history_no_matches_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
            textAlign = TextAlign.Center,
        )
    }
}

/** Test tag exposed for instrumented tests asserting the no-matches branch. */
const val TEST_TAG_HISTORY_NO_MATCHES: String = "history_no_matches"
