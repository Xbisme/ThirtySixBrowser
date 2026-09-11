@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.settings.components.AboutSection
import com.raumanian.thirtysix.browser.presentation.settings.components.AppLanguageChooserDialog
import com.raumanian.thirtysix.browser.presentation.settings.components.ClearBrowsingDataDialog
import com.raumanian.thirtysix.browser.presentation.settings.components.ConfirmShortenRetentionDialog
import com.raumanian.thirtysix.browser.presentation.settings.components.HistoryRetentionChooserDialog
import com.raumanian.thirtysix.browser.presentation.settings.components.SearchEngineChooserDialog
import com.raumanian.thirtysix.browser.presentation.settings.components.SettingsActionRow
import com.raumanian.thirtysix.browser.presentation.settings.components.SettingsSectionHeader
import com.raumanian.thirtysix.browser.presentation.settings.components.SettingsSwitchRow
import com.raumanian.thirtysix.browser.presentation.settings.components.SettingsTopBar
import com.raumanian.thirtysix.browser.presentation.settings.components.SettingsValueRow
import com.raumanian.thirtysix.browser.presentation.settings.components.ThemeModeChooserDialog
import com.raumanian.thirtysix.browser.presentation.settings.components.appLanguageLabel
import com.raumanian.thirtysix.browser.presentation.settings.components.historyRetentionLabel
import com.raumanian.thirtysix.browser.presentation.settings.components.searchEngineLabel
import com.raumanian.thirtysix.browser.presentation.settings.components.themeModeLabel

/**
 * Spec 016 — the Settings screen.
 *
 * Replaces the Spec 002 placeholder. One scrolling list in five sections — Appearance,
 * Language, Search, Privacy, About (FR-002) — with each user story adding its rows under its
 * section and its dialog to [SettingsDialogHost]. Back, from the top bar or the system,
 * returns to the browser.
 */
@Composable
fun SettingsScreen(
    navController: NavHostController = rememberNavController(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigateBack: () -> Unit = { navController.popBackStack() }

    // Research R12 — re-read the platform language whenever the screen becomes visible.
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { }
    }
    BackHandler(onBack = navigateBack)
    SettingsSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsTopBar(onBackClick = navigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(TEST_TAG_SETTINGS_LIST),
        ) {
            appearanceSection(state = state, viewModel = viewModel)
            languageSection(state = state, viewModel = viewModel)
            searchSection(state = state, viewModel = viewModel)
            privacySection(state = state, viewModel = viewModel)
            aboutSection(state = state)
        }
    }

    SettingsDialogHost(state = state, viewModel = viewModel)
}

private fun LazyListScope.sectionHeader(key: String, @StringRes titleRes: Int) {
    item(key = key) { SettingsSectionHeader(title = stringResource(titleRes)) }
}

private fun LazyListScope.appearanceSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    sectionHeader(key = KEY_SECTION_APPEARANCE, titleRes = R.string.settings_section_appearance)
    item(key = KEY_ROW_THEME) {
        SettingsValueRow(
            title = stringResource(R.string.settings_theme_title),
            value = themeModeLabel(state.themeMode),
            onClick = viewModel::onThemeRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_THEME_ROW),
        )
    }
    // FR-010 — dynamic color does not exist below Android 12, so neither does its control.
    if (state.isDynamicColorSupported) {
        item(key = KEY_ROW_DYNAMIC_COLOR) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = state.isDynamicColorEnabled,
                onCheckedChange = viewModel::onDynamicColorToggled,
                modifier = Modifier.testTag(TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW),
            )
        }
    }
}

private fun LazyListScope.languageSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    sectionHeader(key = KEY_SECTION_LANGUAGE, titleRes = R.string.settings_section_language)
    item(key = KEY_ROW_LANGUAGE) {
        SettingsValueRow(
            title = stringResource(R.string.settings_language_title),
            value = appLanguageLabel(state.appLanguage),
            onClick = viewModel::onLanguageRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_LANGUAGE_ROW),
        )
    }
}

private fun LazyListScope.searchSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    sectionHeader(key = KEY_SECTION_SEARCH, titleRes = R.string.settings_section_search)
    item(key = KEY_ROW_SEARCH_ENGINE) {
        SettingsValueRow(
            title = stringResource(R.string.settings_search_engine_title),
            value = searchEngineLabel(state.searchEngine),
            onClick = viewModel::onSearchEngineRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW),
        )
    }
}

private fun LazyListScope.privacySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    sectionHeader(key = KEY_SECTION_PRIVACY, titleRes = R.string.settings_section_privacy)
    item(key = KEY_ROW_RETENTION) {
        SettingsValueRow(
            title = stringResource(R.string.settings_history_retention_title),
            value = historyRetentionLabel(state.historyRetention),
            onClick = viewModel::onRetentionRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_RETENTION_ROW),
        )
    }
    item(key = KEY_ROW_CLEAR_BROWSING_DATA) {
        SettingsActionRow(
            title = stringResource(R.string.settings_clear_browsing_data_title),
            onClick = viewModel::onClearBrowsingDataRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_CLEAR_ROW),
        )
    }
}

private fun LazyListScope.aboutSection(state: SettingsUiState) {
    sectionHeader(key = KEY_SECTION_ABOUT, titleRes = R.string.settings_section_about)
    item(key = KEY_ROW_ABOUT) {
        AboutSection(
            appVersionName = state.appVersionName,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_ABOUT),
        )
    }
}

/** Renders whichever dialog [SettingsUiState.dialog] names — at most one at a time. */
@Composable
private fun SettingsDialogHost(state: SettingsUiState, viewModel: SettingsViewModel) {
    when (val dialog = state.dialog) {
        SettingsDialog.ThemeChooser -> ThemeModeChooserDialog(
            selected = state.themeMode,
            onSelect = viewModel::onThemeSelected,
            onDismiss = viewModel::dismissDialog,
        )
        SettingsDialog.SearchEngineChooser -> SearchEngineChooserDialog(
            selected = state.searchEngine,
            onSelect = viewModel::onSearchEngineSelected,
            onDismiss = viewModel::dismissDialog,
        )
        SettingsDialog.LanguageChooser -> AppLanguageChooserDialog(
            selected = state.appLanguage,
            onSelect = viewModel::onLanguageSelected,
            onDismiss = viewModel::dismissDialog,
        )
        is SettingsDialog.ClearBrowsingData -> ClearBrowsingDataDialog(
            selection = dialog.selection,
            inProgress = dialog.inProgress,
            onToggle = viewModel::onClearCategoryToggled,
            onConfirm = viewModel::onClearBrowsingDataConfirmed,
            onDismiss = viewModel::onClearBrowsingDataDismissed,
        )
        SettingsDialog.RetentionChooser -> HistoryRetentionChooserDialog(
            selected = state.historyRetention,
            onSelect = viewModel::onRetentionSelected,
            onDismiss = viewModel::dismissDialog,
        )
        is SettingsDialog.ConfirmShortenRetention -> ConfirmShortenRetentionDialog(
            target = dialog.target,
            onConfirm = viewModel::onShortenRetentionConfirmed,
            onCancel = viewModel::onShortenRetentionCancelled,
        )
        null -> Unit
    }
}

/**
 * Maps each one-shot [SettingsEvent] to a localized snackbar. Extracted so [SettingsScreen]
 * stays under detekt's `LongMethod` ceiling as the stories add events.
 */
@Composable
private fun SettingsSnackbarEffect(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val notSaved = stringResource(R.string.settings_error_not_saved)
    val languageNotApplied = stringResource(R.string.settings_error_language_not_applied)
    val cleared = stringResource(R.string.settings_clear_done)
    val partiallyCleared = stringResource(R.string.settings_clear_partial_failure)
    val pruneDeferred = stringResource(R.string.settings_retention_prune_deferred)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
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
}

// Stable LazyColumn keys — Constitution §V forbids index-only keys.
private const val KEY_SECTION_APPEARANCE: String = "section_appearance"
private const val KEY_SECTION_LANGUAGE: String = "section_language"
private const val KEY_SECTION_SEARCH: String = "section_search"
private const val KEY_SECTION_PRIVACY: String = "section_privacy"
private const val KEY_SECTION_ABOUT: String = "section_about"
private const val KEY_ROW_THEME: String = "row_theme"
private const val KEY_ROW_DYNAMIC_COLOR: String = "row_dynamic_color"
private const val KEY_ROW_LANGUAGE: String = "row_language"
private const val KEY_ROW_SEARCH_ENGINE: String = "row_search_engine"
private const val KEY_ROW_RETENTION: String = "row_retention"
private const val KEY_ROW_CLEAR_BROWSING_DATA: String = "row_clear_browsing_data"
private const val KEY_ROW_ABOUT: String = "row_about"

const val TEST_TAG_SETTINGS_LIST: String = "settings_list"
const val TEST_TAG_SETTINGS_THEME_ROW: String = "settings_theme_row"
const val TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW: String = "settings_dynamic_color_row"
const val TEST_TAG_SETTINGS_LANGUAGE_ROW: String = "settings_language_row"
const val TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW: String = "settings_search_engine_row"
const val TEST_TAG_SETTINGS_RETENTION_ROW: String = "settings_retention_row"
const val TEST_TAG_SETTINGS_CLEAR_ROW: String = "settings_clear_row"
const val TEST_TAG_SETTINGS_ABOUT: String = "settings_about"
