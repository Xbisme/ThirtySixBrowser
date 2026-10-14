@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting
/**
 * Opens a Settings chooser over the browser from a hub tile, reusing the Settings dialogs and view
 * model. It stays composed after its dialog closes, so a late result still reaches the snackbar.
 */
@Composable
internal fun QuickSettingsHost(
    request: QuickSetting?,
    onConsumeRequest: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val currentOnConsumeRequest by rememberUpdatedState(onConsumeRequest)
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
        currentOnConsumeRequest()
    }
    SettingsDialogHost(state = state, viewModel = viewModel)
    SettingsSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)
}
