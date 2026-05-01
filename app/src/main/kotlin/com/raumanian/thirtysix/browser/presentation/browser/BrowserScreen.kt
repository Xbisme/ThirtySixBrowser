@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserErrorState
import com.raumanian.thirtysix.browser.presentation.browser.components.BrowserLoadingIndicator

/**
 * Spec 007 — top-level Browser screen.
 *
 * Three render branches based on `state.loadingState`:
 * - [LoadingState.Failed] → full-screen [BrowserErrorState] (US3)
 * - otherwise → [BrowserWebView] + optional top [BrowserLoadingIndicator] (US1+US2)
 */
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val loading = state.loadingState) {
            is LoadingState.Failed -> {
                BrowserErrorState(
                    reason = loading.reason,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                BrowserWebView(
                    state = state,
                    callbacks = BrowserWebViewCallbacks(
                        onLoadStarted = viewModel::onLoadStarted,
                        onProgressChanged = viewModel::onProgressChanged,
                        onLoadFinished = viewModel::onLoadFinished,
                        onLoadFailed = viewModel::onLoadFailed,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading is LoadingState.Loading) {
                    BrowserLoadingIndicator(
                        progress = loading.progress,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}
