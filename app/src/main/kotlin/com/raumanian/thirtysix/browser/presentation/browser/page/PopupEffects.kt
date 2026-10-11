@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import kotlinx.coroutines.launch

/**
 * Spec 021 C2.5 — the blocked pop-up notice (with Open when an address is known, FR-026a) and
 * the tab-limit message for a window that cannot open (FR-028). A notice, not a dialog: the page
 * keeps working underneath.
 */
@Composable
fun PopupEffects(popups: PopupTabs, snackbarHostState: SnackbarHostState) {
    val blocked = stringResource(R.string.popup_blocked)
    val open = stringResource(R.string.popup_open)
    val tabLimit = stringResource(R.string.browser_max_tabs_reached)
    LaunchedEffect(popups) {
        popups.events.collect { event ->
            when (event) {
                is PopupEvent.Blocked -> launch {
                    val url = event.openUrl
                    val result = snackbarHostState.showSnackbar(message = blocked, actionLabel = url?.let { open })
                    if (url != null && result == SnackbarResult.ActionPerformed) {
                        popups.openBlocked(url, event.incognito)
                    }
                }
                PopupEvent.TabLimitReached -> launch { snackbarHostState.showSnackbar(tabLimit) }
            }
        }
    }
}
