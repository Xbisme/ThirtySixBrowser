package com.raumanian.thirtysix.browser.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.HistoryRetentionChangeResult
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ClearBrowsingDataUseCase
import com.raumanian.thirtysix.browser.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetDynamicColorEnabledUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetThemeModeUseCase
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
 * Spec 016 — view model for the Settings screen.
 *
 * Mirrors the persisted `UserSettings` snapshot into [uiState], owns which dialog is open,
 * and reports one-shot outcomes on [events]. Every change goes through a domain use case —
 * never a repository — per Constitution §IV; each user story adds its use cases and handlers.
 *
 * The two platform facts arrive as injected values rather than being read from `Build` or
 * `BuildConfig` here, so every branch is testable on the JVM (research.md R9, R10).
 */
@HiltViewModel
// Coordinates one use case per setting across seven user stories, so both the constructor and
// the handler count grow past detekt's defaults by design — the same trade-off HistoryViewModel
// documents for the screens that aggregate several domains.
@Suppress("TooManyFunctions", "LongParameterList")
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
) : ViewModel() {

    private val _uiState: MutableStateFlow<SettingsUiState> = MutableStateFlow(
        SettingsUiState(
            isDynamicColorSupported = supportsDynamicColor,
            appLanguage = getAppLanguage(),
            appVersionName = appVersionName,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * One-shot outcomes. `replay = 0` so a re-subscribing Composable never re-shows a stale
     * message; `DROP_OLDEST` with a one-slot buffer so a burst surfaces the latest outcome
     * without suspending the emitter — the Spec 013 channel policy Specs 014 and 015 reused.
     */
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
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Research R12 — called each time the screen enters STARTED. A language change, in-app or
     * from Android 13+ system settings, recreates the activity but keeps this view model, so a
     * value read only at creation would go stale exactly when it matters (FR-016).
     */
    fun onScreenStarted() {
        refreshAppLanguage()
    }

    /** Closes whichever dialog is open. Outside tap, system back and Cancel all end up here. */
    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    // ──────── US1 — theme ────────

    fun onThemeRowClick() {
        openDialog(SettingsDialog.ThemeChooser)
    }

    /** FR-006 — re-selecting the current mode closes the chooser and writes nothing. */
    fun onThemeSelected(mode: ThemeMode) {
        dismissDialog()
        if (mode == _uiState.value.themeMode) return
        persist { setThemeMode(mode) }
    }

    // ──────── US2 — search engine ────────

    fun onSearchEngineRowClick() {
        openDialog(SettingsDialog.SearchEngineChooser)
    }

    /** FR-006 — re-selecting the current engine closes the chooser and writes nothing. */
    fun onSearchEngineSelected(engine: SearchEngine) {
        dismissDialog()
        if (engine == _uiState.value.searchEngine) return
        persist { setSearchEngine(engine) }
    }

    // ──────── US3 — app language ────────

    fun onLanguageRowClick() {
        openDialog(SettingsDialog.LanguageChooser)
    }

    /**
     * FR-013 – FR-015. The use case skips the platform when [language] is already held
     * (FR-006). On success the platform recreates the activity; the state is refreshed anyway,
     * so it is right even where no recreation follows.
     */
    fun onLanguageSelected(language: AppLanguage) {
        dismissDialog()
        viewModelScope.launch {
            if (!setAppLanguage(language)) _events.tryEmit(SettingsEvent.LanguageNotApplied)
            refreshAppLanguage()
        }
    }

    // ──────── US4 — clear browsing data ────────

    /** FR-025 — the dialog opens with every category selected. */
    fun onClearBrowsingDataRowClick() {
        openDialog(
            SettingsDialog.ClearBrowsingData(
                selection = ClearBrowsingDataCategory.entries.toSet(),
                inProgress = false,
            ),
        )
    }

    /** Flips one category. Ignored while a clear runs, so the selection cannot change mid-way. */
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

    /**
     * FR-026, FR-032, FR-033 — runs the clear exactly once. Ignored while nothing is selected or
     * a clear is already running. The dialog stays open, in progress, until the clear finishes,
     * then closes and reports the outcome.
     */
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

    /** FR-032 — a running clear cannot be dismissed; an idle dialog closes like any other. */
    fun onClearBrowsingDataDismissed() {
        val dialog = _uiState.value.dialog as? SettingsDialog.ClearBrowsingData
        if (dialog?.inProgress == true) return
        dismissDialog()
    }

    // ──────── US5 — history retention ────────

    fun onRetentionRowClick() {
        openDialog(SettingsDialog.RetentionChooser)
    }

    /**
     * FR-006 the same window changes nothing; FR-021 a shorter one asks first, because it
     * deletes history; FR-023 a longer one applies at once.
     */
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

    /** FR-021 — cancelling the warning leaves both the window and the history untouched. */
    fun onShortenRetentionCancelled() {
        dismissDialog()
    }

    // ──────── US6 — dynamic color ────────

    /** FR-009 — a no-op when unchanged; a failed write is reported (FR-043). */
    fun onDynamicColorToggled(enabled: Boolean) {
        if (enabled == _uiState.value.isDynamicColorEnabled) return
        persist { setDynamicColorEnabled(enabled) }
    }

    // ──────── shared ────────

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

    /**
     * FR-043 — runs one settings write. A failure leaves the previous value in effect, because
     * the state only ever follows the persisted snapshot, and is reported as
     * [SettingsEvent.SettingNotSaved].
     */
    private fun persist(write: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            if (write() is Result.Error) _events.tryEmit(SettingsEvent.SettingNotSaved)
        }
    }
}
