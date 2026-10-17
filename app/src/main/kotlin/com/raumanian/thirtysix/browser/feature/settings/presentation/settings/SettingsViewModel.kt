package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.feature.settings.domain.model.HistoryRetentionChangeResult
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ClearBrowsingDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Mirrors the persisted settings into [uiState], owns which dialog is open and reports one-shot
 * outcomes on [events]. Platform facts are injected rather than read from `Build`, so every
 * branch is testable on the JVM.
 */
@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList") // one handler per setting
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val languages: AppLanguageController,
    private val setAppLanguage: SetAppLanguageUseCase,
    private val clearBrowsingData: ClearBrowsingDataUseCase,
    private val changeHistoryRetention: ChangeHistoryRetentionUseCase,
    private val defaultBrowser: DefaultBrowserController,
    @Named(AppConstants.QUALIFIER_SUPPORTS_DYNAMIC_COLOR) supportsDynamicColor: Boolean,
    @Named(AppConstants.QUALIFIER_APP_VERSION_NAME) appVersionName: String,
) : ViewModel() {

    private val _uiState: MutableStateFlow<SettingsUiState> = MutableStateFlow(
        SettingsUiState(
            isDynamicColorSupported = supportsDynamicColor,
            appLanguage = languages.current(),
            appVersionName = appVersionName,
            isDefaultBrowser = defaultBrowser.isDefaultBrowser(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // No replay, so a re-subscribing screen never re-shows a stale message; a burst keeps the latest.
    private val _events: MutableSharedFlow<SettingsEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<SettingsEvent> = _events.asSharedFlow()

    init {
        settings.observeSettings()
            .onEach { stored ->
                _uiState.update { state ->
                    state.copy(
                        themeMode = stored.themeMode,
                        isDynamicColorEnabled = stored.isDynamicColorEnabled,
                        searchEngine = stored.searchEngine,
                        historyRetention = stored.historyRetention,
                        textSize = stored.textSize,
                        isSwipeNavigationEnabled = stored.isSwipeNavigationEnabled,
                        isTabSwipeEnabled = stored.isTabSwipeEnabled,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Started -> onScreenStarted()
            SettingsAction.Resumed -> refreshDefaultBrowser()
            SettingsAction.DismissDialog -> dismissDialog()
            SettingsAction.ThemeRowClick -> openDialog(SettingsDialog.ThemeChooser)
            is SettingsAction.ThemeSelected -> onThemeSelected(action.mode)
            SettingsAction.TextSizeRowClick -> openDialog(SettingsDialog.TextSizeChooser)
            is SettingsAction.TextSizeSelected -> onTextSizeSelected(action.size)
            SettingsAction.SearchEngineRowClick -> openDialog(SettingsDialog.SearchEngineChooser)
            is SettingsAction.SearchEngineSelected -> onSearchEngineSelected(action.engine)
            SettingsAction.LanguageRowClick -> openDialog(SettingsDialog.LanguageChooser)
            is SettingsAction.LanguageSelected -> onLanguageSelected(action.language)
            SettingsAction.LanguageApplied -> dismissDialog()
            SettingsAction.ClearBrowsingDataRowClick -> onClearBrowsingDataRowClick()
            is SettingsAction.ClearCategoryToggled -> onClearCategoryToggled(action.category)
            SettingsAction.ClearBrowsingDataConfirmed -> onClearBrowsingDataConfirmed()
            SettingsAction.ClearBrowsingDataDismissed -> onClearBrowsingDataDismissed()
            SettingsAction.RetentionRowClick -> openDialog(SettingsDialog.RetentionChooser)
            is SettingsAction.RetentionSelected -> onRetentionSelected(action.retention)
            SettingsAction.ShortenRetentionConfirmed -> onShortenRetentionConfirmed()
            SettingsAction.ShortenRetentionCancelled -> dismissDialog()
            is SettingsAction.DynamicColorToggled -> onDynamicColorToggled(action.enabled)
            is SettingsAction.SwipeNavigationToggled -> onSwipeNavigationToggled(action.enabled)
            is SettingsAction.TabSwipeToggled -> onTabSwipeToggled(action.enabled)
        }
    }

    // The language can change in system settings while this view model lives on, so re-read it on every start.
    private fun onScreenStarted() {
        refreshAppLanguage()
    }

    // Called on resume: the system's default-browser dialog is translucent, so the activity is never restarted.
    private fun refreshDefaultBrowser() {
        _uiState.update { it.copy(isDefaultBrowser = defaultBrowser.isDefaultBrowser()) }
    }

    private fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    private fun onThemeSelected(mode: ThemeMode) {
        dismissDialog()
        if (mode == _uiState.value.themeMode) return
        persist { settings.setThemeMode(mode) }
    }

    private fun onTextSizeSelected(size: TextSize) {
        dismissDialog()
        if (size == _uiState.value.textSize) return
        persist { settings.setTextSize(size) }
    }

    private fun onSearchEngineSelected(engine: SearchEngine) {
        dismissDialog()
        if (engine == _uiState.value.searchEngine) return
        persist { settings.setSearchEngine(engine) }
    }

    // The chooser stays open until the screen reports the new language has landed (LanguageApplied);
    // closing it first shows the old language for a few frames, which reads as a flicker.
    private fun onLanguageSelected(language: AppLanguage) {
        if (language == _uiState.value.appLanguage) {
            dismissDialog()
            return
        }
        viewModelScope.launch {
            if (!setAppLanguage(language)) {
                dismissDialog()
                _events.tryEmit(SettingsEvent.LanguageNotApplied)
            }
            refreshAppLanguage()
        }
    }

    private fun onClearBrowsingDataRowClick() {
        openDialog(
            SettingsDialog.ClearBrowsingData(
                selection = ClearBrowsingDataCategory.DEFAULT_SELECTION,
                inProgress = false,
            ),
        )
    }

    // Ignored while a clear runs, so the selection cannot change mid-way.
    private fun onClearCategoryToggled(category: ClearBrowsingDataCategory) {
        _uiState.update { state ->
            val dialog = state.dialog as? SettingsDialog.ClearBrowsingData
            if (dialog == null || dialog.inProgress) return@update state
            val selection = if (category in dialog.selection) {
                dialog.selection - category
            } else {
                dialog.selection + category
            }
            state.copy(dialog = dialog.copy(selection = selection))
        }
    }

    // Runs the clear exactly once; the dialog stays open, in progress, until it finishes.
    private fun onClearBrowsingDataConfirmed() {
        val dialog = _uiState.value.dialog as? SettingsDialog.ClearBrowsingData
        if (dialog == null || dialog.inProgress || dialog.selection.isEmpty()) return
        _uiState.update { it.copy(dialog = dialog.copy(inProgress = true)) }
        viewModelScope.launch {
            val result = clearBrowsingData(dialog.selection)
            dismissDialog()
            _events.tryEmit(
                if (result.failed.isEmpty()) {
                    SettingsEvent.BrowsingDataCleared
                } else {
                    SettingsEvent.BrowsingDataPartiallyCleared
                },
            )
        }
    }

    // A running clear cannot be dismissed.
    private fun onClearBrowsingDataDismissed() {
        val dialog = _uiState.value.dialog as? SettingsDialog.ClearBrowsingData
        if (dialog?.inProgress == true) return
        dismissDialog()
    }

    // A shorter window deletes history, so it asks first; a longer one applies at once.
    private fun onRetentionSelected(retention: HistoryRetention) {
        val current = _uiState.value.historyRetention
        when {
            retention == current -> dismissDialog()
            retention.isShorterThan(current) -> openDialog(SettingsDialog.ConfirmShortenRetention(retention))
            else -> {
                dismissDialog()
                applyRetention(retention)
            }
        }
    }

    private fun onShortenRetentionConfirmed() {
        val dialog = _uiState.value.dialog as? SettingsDialog.ConfirmShortenRetention ?: return
        dismissDialog()
        applyRetention(dialog.target)
    }

    private fun onDynamicColorToggled(enabled: Boolean) {
        if (enabled == _uiState.value.isDynamicColorEnabled) return
        persist { settings.setDynamicColorEnabled(enabled) }
    }

    private fun onSwipeNavigationToggled(enabled: Boolean) {
        if (enabled == _uiState.value.isSwipeNavigationEnabled) return
        persist { settings.setSwipeNavigationEnabled(enabled) }
    }

    private fun onTabSwipeToggled(enabled: Boolean) {
        if (enabled == _uiState.value.isTabSwipeEnabled) return
        persist { settings.setTabSwipeEnabled(enabled) }
    }

    private fun openDialog(dialog: SettingsDialog) {
        _uiState.update { it.copy(dialog = dialog) }
    }

    private fun refreshAppLanguage() {
        _uiState.update { it.copy(appLanguage = languages.current()) }
    }

    private fun applyRetention(retention: HistoryRetention) {
        viewModelScope.launch {
            when (changeHistoryRetention(retention)) {
                is HistoryRetentionChangeResult.Applied -> Unit
                HistoryRetentionChangeResult.SavedPruneDeferred -> _events.tryEmit(SettingsEvent.RetentionPruneDeferred)
                HistoryRetentionChangeResult.NotSaved -> _events.tryEmit(SettingsEvent.SettingNotSaved)
            }
        }
    }

    // A failed write leaves the previous value on screen, because the state only follows the persisted snapshot.
    private fun persist(write: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            if (write() is Result.Error) _events.tryEmit(SettingsEvent.SettingNotSaved)
        }
    }
}
