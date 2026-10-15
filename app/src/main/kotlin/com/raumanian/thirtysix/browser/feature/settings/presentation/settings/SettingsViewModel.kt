package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetThemeModeUseCase
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.feature.settings.domain.model.HistoryRetentionChangeResult
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ClearBrowsingDataUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.SetDynamicColorEnabledUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.SetTextSizeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
@Suppress("TooManyFunctions", "LongParameterList") // one use case and handler per setting
class SettingsViewModel @Inject constructor(
    observeUserSettings: ObserveUserSettingsUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    private val setDynamicColorEnabled: SetDynamicColorEnabledUseCase,
    private val setSearchEngine: SetSearchEngineUseCase,
    private val getAppLanguage: GetAppLanguageUseCase,
    private val setAppLanguage: SetAppLanguageUseCase,
    private val clearBrowsingData: ClearBrowsingDataUseCase,
    private val changeHistoryRetention: ChangeHistoryRetentionUseCase,
    @Named(AppConstants.QUALIFIER_SUPPORTS_DYNAMIC_COLOR) supportsDynamicColor: Boolean,
    @Named(AppConstants.QUALIFIER_APP_VERSION_NAME) appVersionName: String,
    private val isDefaultBrowser: IsDefaultBrowserUseCase,
    private val setTextSize: SetTextSizeUseCase,
) : ViewModel() {

    private val _uiState: MutableStateFlow<SettingsUiState> = MutableStateFlow(
        SettingsUiState(
            isDynamicColorSupported = supportsDynamicColor,
            appLanguage = getAppLanguage(),
            appVersionName = appVersionName,
            isDefaultBrowser = isDefaultBrowser(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // No replay, so a re-subscribing screen never re-shows a stale message; a burst keeps the latest.
    private val _events: MutableSharedFlow<SettingsEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        observeUserSettings()
            .onEach { settings ->
                _uiState.update { state ->
                    state.copy(
                        themeMode = settings.themeMode,
                        isDynamicColorEnabled = settings.isDynamicColorEnabled,
                        searchEngine = settings.searchEngine,
                        historyRetention = settings.historyRetention,
                        textSize = settings.textSize,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // The language can change in system settings while this view model lives on, so re-read it on every start.
    fun onScreenStarted() {
        refreshAppLanguage()
    }

    // Called on resume: the system's default-browser dialog is translucent, so the activity is never restarted.
    fun refreshDefaultBrowser() {
        _uiState.update { it.copy(isDefaultBrowser = isDefaultBrowser()) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun onThemeRowClick() {
        openDialog(SettingsDialog.ThemeChooser)
    }

    fun onThemeSelected(mode: ThemeMode) {
        dismissDialog()
        if (mode == _uiState.value.themeMode) return
        persist { setThemeMode(mode) }
    }

    fun onTextSizeRowClick() {
        openDialog(SettingsDialog.TextSizeChooser)
    }

    fun onTextSizeSelected(size: TextSize) {
        dismissDialog()
        if (size == _uiState.value.textSize) return
        persist { setTextSize(size) }
    }

    fun onSearchEngineRowClick() {
        openDialog(SettingsDialog.SearchEngineChooser)
    }

    fun onSearchEngineSelected(engine: SearchEngine) {
        dismissDialog()
        if (engine == _uiState.value.searchEngine) return
        persist { setSearchEngine(engine) }
    }

    fun onLanguageRowClick() {
        openDialog(SettingsDialog.LanguageChooser)
    }

    // The chooser stays open until the screen reports the new language has landed (onLanguageApplied);
    // closing it first shows the old language for a few frames, which reads as a flicker.
    fun onLanguageSelected(language: AppLanguage) {
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

    /** The new language is on screen, or the wait timed out. */
    fun onLanguageApplied() {
        dismissDialog()
    }

    fun onClearBrowsingDataRowClick() {
        openDialog(
            SettingsDialog.ClearBrowsingData(
                selection = ClearBrowsingDataCategory.DEFAULT_SELECTION,
                inProgress = false,
            ),
        )
    }

    // Ignored while a clear runs, so the selection cannot change mid-way.
    fun onClearCategoryToggled(category: ClearBrowsingDataCategory) {
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
    fun onClearBrowsingDataConfirmed() {
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
    fun onClearBrowsingDataDismissed() {
        val dialog = _uiState.value.dialog as? SettingsDialog.ClearBrowsingData
        if (dialog?.inProgress == true) return
        dismissDialog()
    }

    fun onRetentionRowClick() {
        openDialog(SettingsDialog.RetentionChooser)
    }

    // A shorter window deletes history, so it asks first; a longer one applies at once.
    fun onRetentionSelected(retention: HistoryRetention) {
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

    fun onShortenRetentionConfirmed() {
        val dialog = _uiState.value.dialog as? SettingsDialog.ConfirmShortenRetention ?: return
        dismissDialog()
        applyRetention(dialog.target)
    }

    fun onShortenRetentionCancelled() {
        dismissDialog()
    }

    fun onDynamicColorToggled(enabled: Boolean) {
        if (enabled == _uiState.value.isDynamicColorEnabled) return
        persist { setDynamicColorEnabled(enabled) }
    }

    private fun openDialog(dialog: SettingsDialog) {
        _uiState.update { it.copy(dialog = dialog) }
    }

    private fun refreshAppLanguage() {
        _uiState.update { it.copy(appLanguage = getAppLanguage()) }
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
