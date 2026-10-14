package com.raumanian.thirtysix.browser.feature.history.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.history.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.feature.history.domain.model.historyDayBucketOf
import com.raumanian.thirtysix.browser.feature.history.domain.usecase.ClearAllHistoryUseCase
import com.raumanian.thirtysix.browser.feature.history.domain.usecase.DeleteHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.history.domain.usecase.ObserveHistoryEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
class HistoryViewModel @Inject constructor(
    observeHistoryEntries: ObserveHistoryEntriesUseCase,
    observeActiveTab: ObserveActiveTabUseCase,
    observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val createTab: CreateTabUseCase,
    private val deleteHistoryEntry: DeleteHistoryEntryUseCase,
    private val clearAllHistory: ClearAllHistoryUseCase,
    private val clipboardWriter: ClipboardWriter,
    dispatchers: DispatcherProvider,
    val faviconCache: FaviconCache,
) : ViewModel() {

    private val _uiState: MutableStateFlow<HistoryUiState> = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // `replay = 0` so a re-subscribing Composable never re-shows a stale message; `DROP_OLDEST`
    // with one slot so a burst of actions surfaces the latest outcome without suspending.
    private val _historySnackbarEvent: MutableSharedFlow<HistoryErrorEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val historySnackbarEvent: SharedFlow<HistoryErrorEvent> = _historySnackbarEvent.asSharedFlow()

    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")

    private var activeTabId: Long = 0L

    private var lastKnownIsIncognito: Boolean = false

    init {
        // Re-derives on every repository emission and keystroke, so new matches appear under an active query.
        combine(observeHistoryEntries(), searchQueryFlow) { entries, query -> entries to query }
            .map { (entries, query) -> Derived(entries, groupByDay(filterEntries(entries, query))) }
            // The O(n) filter and bucketing would block the UI thread on every keystroke at 10 000 rows;
            // `flowOn` moves only the upstream, so the state write in `onEach` stays on main.
            .flowOn(dispatchers.default)
            .onEach { derived ->
                _uiState.update { state ->
                    state.copy(entries = derived.entries, groupedEntries = derived.groups)
                }
            }
            .launchIn(viewModelScope)

        observeActiveTab()
            .onEach { tab -> activeTabId = tab?.id ?: 0L }
            .launchIn(viewModelScope)

        observeActiveTabIsIncognito()
            .onEach { lastKnownIsIncognito = it }
            .launchIn(viewModelScope)
    }

    fun onEntryTap(entry: HistoryEntry) {
        viewModelScope.launch {
            if (activeTabId > 0L) {
                updateActiveTabUrlAndTitle(
                    tabId = activeTabId,
                    url = entry.url,
                    title = entry.title,
                    isIncognito = lastKnownIsIncognito,
                )
            }
            _uiState.update { state -> state.copy(openUrl = entry.url) }
        }
    }

    fun consumeOpenUrl() {
        _uiState.update { state -> state.copy(openUrl = null) }
    }

    // Mirrored synchronously so the field stays responsive; truncated so the per-keystroke scan
    // never sees a huge query.
    fun onSearchQueryChange(query: String) {
        val bounded = query.take(BrowserLimits.MAX_HISTORY_QUERY_LENGTH)
        _uiState.update { state -> state.copy(searchQuery = bounded) }
        searchQueryFlow.value = bounded
    }

    fun onSearchQueryClear() {
        onSearchQueryChange("")
    }

    fun onLongPressEntry(entry: HistoryEntry) {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = entry) }
    }

    fun onActionSheetDismiss() {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
    }

    /**
     * Always opens a normal tab, even from an incognito one. On a cap failure the existing tab-cap
     * message is shown and the screen stays open.
     */
    fun onOpenInNewTab(entry: HistoryEntry) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
            val outcome = runCatching { createTab(entry.url) }
            val capReached = outcome.getOrNull().let { result ->
                result is Result.Error && result.throwable is MaxTabsReachedException
            }
            when {
                outcome.isFailure || capReached ->
                    _historySnackbarEvent.tryEmit(HistoryErrorEvent.TabCapReached)
                else ->
                    _uiState.update { state -> state.copy(openUrl = entry.url) }
            }
        }
    }

    /** Removes exactly the targeted row; other visits to the same URL stay. */
    fun onDeleteEntry(entry: HistoryEntry) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
            runCatching { deleteHistoryEntry(entry.id) }
                .onFailure { cause ->
                    _historySnackbarEvent.tryEmit(HistoryErrorEvent.DeletionFailed(cause))
                }
        }
    }

    fun onCopyUrl(entry: HistoryEntry) {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
        if (clipboardWriter.copyUrl(entry.url)) {
            _historySnackbarEvent.tryEmit(HistoryErrorEvent.ClipboardCopied)
        }
    }

    fun onClearAllRequested() {
        _uiState.update { state -> state.copy(isClearAllDialogVisible = true) }
    }

    fun onClearAllCancelled() {
        _uiState.update { state -> state.copy(isClearAllDialogVisible = false) }
    }

    fun onClearAllConfirmed() {
        viewModelScope.launch {
            try {
                runCatching { clearAllHistory() }
                    .onFailure { cause ->
                        _historySnackbarEvent.tryEmit(HistoryErrorEvent.DeletionFailed(cause))
                    }
            } finally {
                _uiState.update { state -> state.copy(isClearAllDialogVisible = false) }
            }
        }
    }

    private data class Derived(
        val entries: List<HistoryEntry>,
        val groups: List<HistoryUiState.DayGroup>,
    )

    // Below the threshold the list passes through. `String.contains` treats `%`, `_` and `'` literally,
    // which is why search is never done in SQL `LIKE`.
    private fun filterEntries(entries: List<HistoryEntry>, query: String): List<HistoryEntry> {
        if (query.length < BrowserLimits.SEARCH_MIN_CHARS) return entries
        return entries.filter { entry ->
            entry.title.contains(query, ignoreCase = true) ||
                entry.url.contains(query, ignoreCase = true)
        }
    }

    // Keeps input order within each day; days with no entries are dropped, so they vanish under a query.
    private fun groupByDay(entries: List<HistoryEntry>): List<HistoryUiState.DayGroup> {
        if (entries.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val buckets = linkedMapOf<HistoryDayBucket, MutableList<HistoryEntry>>()
        for (entry in entries) {
            val bucket = historyDayBucketOf(entry.visitedAt, today, zone)
            buckets.getOrPut(bucket) { mutableListOf() }.add(entry)
        }
        return buckets.map { (bucket, list) -> HistoryUiState.DayGroup(bucket, list) }
    }
}
