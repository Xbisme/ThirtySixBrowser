@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.AppLanguageChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.ClearBrowsingDataDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.ConfirmShortenRetentionDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.HistoryRetentionChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.SearchEngineChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.TextSizeChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.ThemeModeChooserDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Keeps the language chooser open until the new configuration arrives, then closes it in that
 * frame, so the person sees one change. Reading the configuration in composition is deliberate.
 */
@Composable
private fun LanguageChooserHost(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnApply by rememberUpdatedState(onApply)
    val locales = LocalConfiguration.current.locales
    val localesWhenOpened = remember { locales }
    var picked by remember { mutableStateOf(false) }
    LaunchedEffect(locales, picked) {
        if (picked && locales != localesWhenOpened) currentOnApply()
    }
    LaunchedEffect(picked) {
        if (picked) {
            delay(BrowserLimits.LANGUAGE_APPLY_TIMEOUT_MS)
            currentOnApply()
        }
    }
    AppLanguageChooserDialog(
        selected = selected,
        onSelect = { language ->
            picked = true
            onSelect(language)
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun SettingsDialogHost(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    when (val dialog = state.dialog) {
        SettingsDialog.ThemeChooser -> ThemeModeChooserDialog(
            selected = state.themeMode,
            onSelect = { onAction(SettingsAction.ThemeSelected(it)) },
            onDismiss = { onAction(SettingsAction.DismissDialog) },
        )
        SettingsDialog.SearchEngineChooser -> SearchEngineChooserDialog(
            selected = state.searchEngine,
            onSelect = { onAction(SettingsAction.SearchEngineSelected(it)) },
            onDismiss = { onAction(SettingsAction.DismissDialog) },
        )
        SettingsDialog.LanguageChooser -> LanguageChooserHost(
            selected = state.appLanguage,
            onSelect = { onAction(SettingsAction.LanguageSelected(it)) },
            onApply = { onAction(SettingsAction.LanguageApplied) },
            onDismiss = { onAction(SettingsAction.DismissDialog) },
        )
        is SettingsDialog.ClearBrowsingData -> ClearBrowsingDataDialog(
            selection = dialog.selection,
            inProgress = dialog.inProgress,
            onToggle = { onAction(SettingsAction.ClearCategoryToggled(it)) },
            onConfirm = { onAction(SettingsAction.ClearBrowsingDataConfirmed) },
            onDismiss = { onAction(SettingsAction.ClearBrowsingDataDismissed) },
        )
        SettingsDialog.RetentionChooser -> HistoryRetentionChooserDialog(
            selected = state.historyRetention,
            onSelect = { onAction(SettingsAction.RetentionSelected(it)) },
            onDismiss = { onAction(SettingsAction.DismissDialog) },
        )
        SettingsDialog.TextSizeChooser -> TextSizeChooserDialog(
            selected = state.textSize,
            onSelect = { onAction(SettingsAction.TextSizeSelected(it)) },
            onDismiss = { onAction(SettingsAction.DismissDialog) },
        )
        is SettingsDialog.ConfirmShortenRetention -> ConfirmShortenRetentionDialog(
            target = dialog.target,
            onConfirm = { onAction(SettingsAction.ShortenRetentionConfirmed) },
            onCancel = { onAction(SettingsAction.ShortenRetentionCancelled) },
        )
        null -> Unit
    }
}

@Composable
internal fun SettingsSnackbarEffect(
    events: Flow<SettingsEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val notSaved = stringResource(R.string.settings_error_not_saved)
    val languageNotApplied = stringResource(R.string.settings_error_language_not_applied)
    val cleared = stringResource(R.string.settings_clear_done)
    val partiallyCleared = stringResource(R.string.settings_clear_partial_failure)
    val pruneDeferred = stringResource(R.string.settings_retention_prune_deferred)

    EventEffect(events) { event ->
        val message = when (event) {
            SettingsEvent.SettingNotSaved -> notSaved
            SettingsEvent.LanguageNotApplied -> languageNotApplied
            SettingsEvent.BrowsingDataCleared -> cleared
            SettingsEvent.BrowsingDataPartiallyCleared -> partiallyCleared
            SettingsEvent.RetentionPruneDeferred -> pruneDeferred
        }
        snackbarHostState.showSnackbar(message)
    }
}
