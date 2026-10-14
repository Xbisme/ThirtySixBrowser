@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsActionRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsIconTone
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsInfoRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsSectionHeader
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsSwitchRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsTopBar
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsValueRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.util.appLanguageLabel
import com.raumanian.thirtysix.browser.core.presentation.util.rememberDefaultBrowserRequest
import com.raumanian.thirtysix.browser.core.presentation.util.searchEngineLabel
import com.raumanian.thirtysix.browser.core.presentation.util.themeModeLabel
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.AboutSection
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.AppLanguageChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.ClearBrowsingDataDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.ConfirmShortenRetentionDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.HistoryRetentionChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.SearchEngineChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.TextSizeChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.ThemeModeChooserDialog
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.historyRetentionLabel
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.textSizeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: SettingsViewModel = hiltViewModel(),
    destinations: SettingsDestinations = SettingsDestinations(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigateBack: () -> Unit = { navController.popBackStack() }

    // The language may have changed in system settings while the screen was hidden.
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { }
    }
    // On resume, not start: the system's default-browser confirmation is translucent.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDefaultBrowser()
        onPauseOrDispose { }
    }
    BackHandler(onBack = navigateBack)
    SettingsSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)
    val scope = rememberCoroutineScope()
    val unavailableMessage = stringResource(R.string.entry_default_browser_unavailable)
    val requestDefaultBrowser = rememberDefaultBrowserRequest(
        onUnavailable = { scope.launch { snackbarHostState.showSnackbar(unavailableMessage) } },
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SettingsTopBar(onBackClick = navigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(TEST_TAG_SETTINGS_LIST),
        ) {
            generalSection(state = state, onMakeDefault = requestDefaultBrowser)
            appearanceSection(state = state, viewModel = viewModel)
            languageAndSearchSection(state = state, viewModel = viewModel)
            privacySection(
                state = state,
                viewModel = viewModel,
                onSitePermissions = destinations.onSitePermissions,
                onProtectionExceptions = destinations.onProtectionExceptions,
                onFireproofSites = destinations.onFireproofSites,
            )
            aboutSection(state = state)
        }
    }

    SettingsDialogHost(state = state, viewModel = viewModel)
}

private fun LazyListScope.sectionHeader(key: String, @StringRes titleRes: Int, showDivider: Boolean = true) {
    item(key = key, contentType = CONTENT_TYPE_HEADER) {
        SettingsSectionHeader(title = stringResource(titleRes), showDivider = showDivider)
    }
}

// When ThirtySix is already the default browser the row is information only.
private fun LazyListScope.generalSection(state: SettingsUiState, onMakeDefault: () -> Unit) {
    sectionHeader(key = KEY_SECTION_GENERAL, titleRes = R.string.settings_section_general, showDivider = false)
    item(key = KEY_ROW_DEFAULT_BROWSER, contentType = CONTENT_TYPE_ROW) {
        val title = stringResource(R.string.settings_default_browser_title)
        val modifier = Modifier.testTag(TEST_TAG_SETTINGS_DEFAULT_BROWSER_ROW)
        if (state.isDefaultBrowser) {
            SettingsInfoRow(
                title = title,
                summary = stringResource(R.string.settings_default_browser_summary_is_default),
                icon = AppIcons.Globe,
                tone = SettingsIconTone.Primary,
                modifier = modifier,
            )
        } else {
            SettingsValueRow(
                title = title,
                value = stringResource(R.string.settings_default_browser_summary_not_default),
                icon = AppIcons.Globe,
                tone = SettingsIconTone.Primary,
                onClick = onMakeDefault,
                modifier = modifier,
            )
        }
    }
}

private fun LazyListScope.appearanceSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    sectionHeader(key = KEY_SECTION_APPEARANCE, titleRes = R.string.settings_section_appearance)
    item(key = KEY_ROW_THEME, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_theme_title),
            value = themeModeLabel(state.themeMode),
            icon = AppIcons.ContrastFilled,
            tone = SettingsIconTone.Sky,
            onClick = viewModel::onThemeRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_THEME_ROW),
        )
    }
    item(key = KEY_ROW_TEXT_SIZE, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_text_size_title),
            value = textSizeLabel(state.textSize),
            icon = AppIcons.TextSize,
            tone = SettingsIconTone.Sky,
            onClick = viewModel::onTextSizeRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_TEXT_SIZE_ROW),
        )
    }
    // Dynamic color does not exist below Android 12, so neither does its control.
    if (state.isDynamicColorSupported) {
        item(key = KEY_ROW_DYNAMIC_COLOR, contentType = CONTENT_TYPE_SWITCH_ROW) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = state.isDynamicColorEnabled,
                icon = AppIcons.PaletteFilled,
                tone = SettingsIconTone.Primary,
                onCheckedChange = viewModel::onDynamicColorToggled,
                modifier = Modifier.testTag(TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW),
            )
        }
    }
}

private fun LazyListScope.languageAndSearchSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    sectionHeader(key = KEY_SECTION_LANGUAGE_AND_SEARCH, titleRes = R.string.settings_section_language_and_search)
    item(key = KEY_ROW_LANGUAGE, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_language_title),
            value = appLanguageLabel(state.appLanguage),
            icon = AppIcons.TranslateFilled,
            tone = SettingsIconTone.Sun,
            onClick = viewModel::onLanguageRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_LANGUAGE_ROW),
        )
    }
    item(key = KEY_ROW_SEARCH_ENGINE, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_search_engine_title),
            value = searchEngineLabel(state.searchEngine),
            icon = AppIcons.SearchEngineFilled,
            tone = SettingsIconTone.Mint,
            onClick = viewModel::onSearchEngineRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW),
        )
    }
}

private fun LazyListScope.privacySection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onSitePermissions: () -> Unit,
    onProtectionExceptions: () -> Unit,
    onFireproofSites: () -> Unit,
) {
    sectionHeader(key = KEY_SECTION_PRIVACY, titleRes = R.string.settings_section_privacy)
    item(key = KEY_ROW_RETENTION, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_history_retention_title),
            value = historyRetentionLabel(state.historyRetention),
            icon = AppIcons.ScheduleFilled,
            tone = SettingsIconTone.Primary,
            onClick = viewModel::onRetentionRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_RETENTION_ROW),
        )
    }
    item(key = KEY_ROW_SITE_PERMISSIONS, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_site_permissions),
            value = stringResource(R.string.settings_site_permissions_summary),
            icon = AppIcons.Shield,
            tone = SettingsIconTone.Primary,
            onClick = onSitePermissions,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_SITE_PERMISSIONS_ROW),
        )
    }
    item(key = KEY_ROW_PROTECTION_EXCEPTIONS, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_protection_exceptions),
            value = stringResource(R.string.settings_protection_exceptions_summary),
            icon = AppIcons.Shield,
            tone = SettingsIconTone.Primary,
            onClick = onProtectionExceptions,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_PROTECTION_EXCEPTIONS_ROW),
        )
    }
    item(key = KEY_ROW_FIREPROOF_SITES, contentType = CONTENT_TYPE_ROW) {
        SettingsValueRow(
            title = stringResource(R.string.settings_fireproof_sites),
            value = stringResource(R.string.settings_fireproof_sites_summary),
            icon = AppIcons.Fire,
            tone = SettingsIconTone.Primary,
            onClick = onFireproofSites,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_FIREPROOF_SITES_ROW),
        )
    }
    item(key = KEY_ROW_CLEAR_BROWSING_DATA, contentType = CONTENT_TYPE_ACTION_ROW) {
        SettingsActionRow(
            title = stringResource(R.string.settings_clear_browsing_data_title),
            icon = AppIcons.MopFilled,
            tone = SettingsIconTone.Peach,
            onClick = viewModel::onClearBrowsingDataRowClick,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_CLEAR_ROW),
        )
    }
}

private fun LazyListScope.aboutSection(state: SettingsUiState) {
    sectionHeader(key = KEY_SECTION_ABOUT, titleRes = R.string.settings_section_about)
    item(key = KEY_ROW_ABOUT, contentType = CONTENT_TYPE_ABOUT) {
        AboutSection(
            appVersionName = state.appVersionName,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_ABOUT),
        )
    }
}

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
internal fun SettingsDialogHost(state: SettingsUiState, viewModel: SettingsViewModel) {
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
        SettingsDialog.LanguageChooser -> LanguageChooserHost(
            selected = state.appLanguage,
            onSelect = viewModel::onLanguageSelected,
            onApply = viewModel::onLanguageApplied,
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
        SettingsDialog.TextSizeChooser -> TextSizeChooserDialog(
            selected = state.textSize,
            onSelect = viewModel::onTextSizeSelected,
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

@Composable
internal fun SettingsSnackbarEffect(
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

private const val KEY_SECTION_GENERAL: String = "section_general"
private const val KEY_SECTION_APPEARANCE: String = "section_appearance"
private const val KEY_SECTION_LANGUAGE_AND_SEARCH: String = "section_language_and_search"
private const val KEY_SECTION_PRIVACY: String = "section_privacy"
private const val KEY_SECTION_ABOUT: String = "section_about"
private const val KEY_ROW_DEFAULT_BROWSER: String = "row_default_browser"
private const val KEY_ROW_THEME: String = "row_theme"
private const val KEY_ROW_DYNAMIC_COLOR: String = "row_dynamic_color"
private const val KEY_ROW_TEXT_SIZE: String = "row_text_size"
private const val KEY_ROW_LANGUAGE: String = "row_language"
private const val KEY_ROW_SEARCH_ENGINE: String = "row_search_engine"
private const val KEY_ROW_RETENTION: String = "row_retention"
private const val KEY_ROW_CLEAR_BROWSING_DATA: String = "row_clear_browsing_data"
private const val KEY_ROW_SITE_PERMISSIONS: String = "row_site_permissions"
private const val KEY_ROW_PROTECTION_EXCEPTIONS: String = "row_protection_exceptions"
private const val KEY_ROW_FIREPROOF_SITES: String = "row_fireproof_sites"
private const val KEY_ROW_ABOUT: String = "row_about"
private const val CONTENT_TYPE_HEADER: String = "header"
private const val CONTENT_TYPE_ROW: String = "value_row"
private const val CONTENT_TYPE_SWITCH_ROW: String = "switch_row"
private const val CONTENT_TYPE_ACTION_ROW: String = "action_row"
private const val CONTENT_TYPE_ABOUT: String = "about"

const val TEST_TAG_SETTINGS_LIST: String = "settings_list"
const val TEST_TAG_SETTINGS_DEFAULT_BROWSER_ROW: String = "settings_default_browser_row"
const val TEST_TAG_SETTINGS_THEME_ROW: String = "settings_theme_row"
const val TEST_TAG_SETTINGS_TEXT_SIZE_ROW: String = "settings_text_size_row"
const val TEST_TAG_SETTINGS_DYNAMIC_COLOR_ROW: String = "settings_dynamic_color_row"
const val TEST_TAG_SETTINGS_LANGUAGE_ROW: String = "settings_language_row"
const val TEST_TAG_SETTINGS_SEARCH_ENGINE_ROW: String = "settings_search_engine_row"
const val TEST_TAG_SETTINGS_RETENTION_ROW: String = "settings_retention_row"
const val TEST_TAG_SETTINGS_CLEAR_ROW: String = "settings_clear_row"
const val TEST_TAG_SETTINGS_SITE_PERMISSIONS_ROW: String = "settings_site_permissions_row"
const val TEST_TAG_SETTINGS_PROTECTION_EXCEPTIONS_ROW: String = "settings_protection_exceptions_row"
const val TEST_TAG_SETTINGS_FIREPROOF_SITES_ROW: String = "settings_fireproof_sites_row"
const val TEST_TAG_SETTINGS_ABOUT: String = "settings_about"
