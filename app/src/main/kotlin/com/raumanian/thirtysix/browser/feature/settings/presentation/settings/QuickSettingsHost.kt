@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting
/**
 * Spec 019 FR-031 — opens one of the existing Settings choosers over the browser when a hub
 * quick-setting tile is tapped. It reuses Spec 016's dialogs and their view model unchanged,
 * so a choice made here behaves exactly as the same choice made in Settings.
 *
 * Composed only once the user has asked for a quick setting, so the browser does not create a
 * Settings view model it may never use. It stays composed afterwards so that a result arriving
 * after its dialog closes (Clear data finishing, a language that could not be applied) still
 * reaches [snackbarHostState].
 */
@Composable
internal fun QuickSettingsHost(
    request: QuickSetting?,
    onRequestHandled: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        when (request) {
            QuickSetting.Theme -> viewModel.onThemeRowClick()
            QuickSetting.Language -> {
                viewModel.onScreenStarted()
                viewModel.onLanguageRowClick()
            }
            QuickSetting.SearchEngine -> viewModel.onSearchEngineRowClick()
            QuickSetting.ClearData -> viewModel.onClearBrowsingDataRowClick()
            QuickSetting.TextSize -> viewModel.onTextSizeRowClick()
            null -> return@LaunchedEffect
        }
        onRequestHandled()
    }
    SettingsDialogHost(state = state, viewModel = viewModel)
    SettingsSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)
}
