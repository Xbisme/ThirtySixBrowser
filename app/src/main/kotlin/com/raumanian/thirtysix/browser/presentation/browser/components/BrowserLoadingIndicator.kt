@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.raumanian.thirtysix.browser.R

/**
 * Spec 007 US2 — top-anchored Material3 progress indicator.
 *
 * Driven by `WebChromeClient.onProgressChanged` via `BrowserViewModel`. Sits at
 * the top edge of `BrowserScreen` and never overlaps the WebView content area
 * (FR-003). When Specs 008/009 add a toolbar / address bar above the WebView,
 * this Composable anchors directly beneath them without a structural rewrite.
 *
 * @param progress 0..1 from `LoadingState.Loading.progress`.
 */
@Composable
fun BrowserLoadingIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val a11yLabel = stringResource(R.string.browser_loading_a11y)
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .testTag(TEST_TAG)
            .semantics { contentDescription = a11yLabel },
    )
}

const val TEST_TAG_BROWSER_LOADING_INDICATOR: String = "browser_loading_indicator"

private const val TEST_TAG: String = TEST_TAG_BROWSER_LOADING_INDICATOR
