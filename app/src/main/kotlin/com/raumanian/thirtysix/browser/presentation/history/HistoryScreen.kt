@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.presentation.history.components.EmptyHistoryState
import com.raumanian.thirtysix.browser.presentation.history.components.HistoryDayHeader
import com.raumanian.thirtysix.browser.presentation.history.components.HistoryRow
import com.raumanian.thirtysix.browser.presentation.history.components.HistoryTopBar
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination

/**
 * Spec 014 — History screen. US1 surface: top bar + grouped (or empty) list +
 * tap-to-replace-active-tab popBack signal. US2/US3/US4 extend this Composable.
 *
 * `FaviconCache` is exposed via the ViewModel (rather than via Hilt entry points
 * or a separate Composable parameter) so test seams stay simple — instrumented
 * tests can swap the VM with a fake whose `faviconCache` field returns a noop.
 */
@Composable
fun HistoryScreen(
    navController: NavHostController = rememberNavController(),
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Spec 014 FR-010 — when the ViewModel signals a tap-replace, pop back to
    // BrowserScreen so the user immediately sees the new URL load.
    LaunchedEffect(state.openUrl) {
        if (state.openUrl != null) {
            navController.popBackStack(AppDestination.Browser.route, inclusive = false)
            viewModel.consumeOpenUrl()
        }
    }

    Scaffold(
        topBar = { HistoryTopBar(onBackClick = { navController.popBackStack() }) },
    ) { padding ->
        if (state.entries.isEmpty()) {
            EmptyHistoryState(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            for (group in state.groupedEntries) {
                item(key = "header_${group.bucket}") {
                    HistoryDayHeader(bucket = group.bucket)
                }
                items(group.entries, key = { entry -> entry.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        faviconCache = viewModel.faviconCache,
                        onClick = { viewModel.onEntryTap(entry) },
                        onLongClick = { /* US3 — long-press action sheet wired later. */ },
                    )
                }
            }
        }
    }
}
