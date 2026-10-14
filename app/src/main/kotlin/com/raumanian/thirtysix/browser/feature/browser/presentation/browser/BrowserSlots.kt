package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.raumanian.thirtysix.browser.core.presentation.util.FireButton
import com.raumanian.thirtysix.browser.core.presentation.util.IncomingContentHooks
import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting

/** What the browser screen takes from other features, filled in by the navigation graph. */
@Immutable
class BrowserSlots(
    val fireButton: FireButton,
    val fireOverlay: @Composable () -> Unit,
    val quickSettings: @Composable (request: QuickSetting?, onRequestHandled: () -> Unit, snackbar: SnackbarHostState) -> Unit,
    val incomingContent: IncomingContentHooks = IncomingContentHooks(),
    val destinations: BrowserDestinations = BrowserDestinations(),
)

/** Screens of other features that the browser opens. */
@Immutable
data class BrowserDestinations(
    val openTabs: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val openDownloads: () -> Unit = {},
    val openHistory: () -> Unit = {},
    val openBookmarks: () -> Unit = {},
)
