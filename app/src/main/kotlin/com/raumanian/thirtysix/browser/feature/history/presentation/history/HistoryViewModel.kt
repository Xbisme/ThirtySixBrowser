package com.raumanian.thirtysix.browser.feature.history.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ClearBrowsingHistoryUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.history.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.feature.history.domain.model.historyDayBucketOf
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val clearHistory: ClearBrowsingHistoryUseCase,
    observeActiveTab: ObserveActiveTabUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val tabs: TabRepository,
    private val clipboardWriter: ClipboardWriter,
    dispatchers: DispatcherProvider,
    val faviconCache: FaviconCache,
) : ViewModel() {

    private val _uiState: MutableStateFlow<HistoryUiState> = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // `replay = 0` so a re-subscribing Composable never re-shows a stale message; `DROP_OLDEST`
    // with one slot so a burst of actions surfaces the latest outcome without suspending.
    private val snackbarEvents: MutableSharedFlow<HistoryErrorEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Kept until the screen collects it, as the consumed-once state field it replaces was.
    private val openUrls = Channel<String>(Channel.BUFFERED)

    val events: Flow<HistoryEvent> = merge(
        openUrls.receiveAsFlow().map { HistoryEvent.OpenUrl(it) },
        snackbarEvents.map { HistoryEvent.Snackbar(it) },
    )

    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")

    private var activeTabId: Long = 0L

    init {
        // Re-derives on every repository emission and keystroke, so new matches appear under an active query.
        combine(history.observeAll(), searchQueryFlow) { entries, query -> entries to query }
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
    }

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.EntryTap -> onEntryTap(action.entry)
            is HistoryAction.LongPressEntry -> onLongPressEntry(action.entry)
            HistoryAction.ActionSheetDismiss -> onActionSheetDismiss()
            is HistoryAction.OpenInNewTab -> onOpenInNewTab(action.entry)
            is HistoryAction.DeleteEntry -> onDeleteEntry(action.entry)
            is HistoryAction.CopyUrl -> onCopyUrl(action.entry)
            is HistoryAction.SearchQueryChange -> onSearchQueryChange(action.query)
            HistoryAction.SearchQueryClear -> onSearchQueryChange("")
            HistoryAction.ClearAllRequested -> onClearAllRequested()
            HistoryAction.ClearAllCancelled -> onClearAllCancelled()
            HistoryAction.ClearAllConfirmed -> onClearAllConfirmed()
        }
    }

    private fun onEntryTap(entry: HistoryEntry) {
        viewModelScope.launch {
            if (activeTabId != 0L) {
                updateActiveTabUrlAndTitle(
                    tabId = activeTabId,
                    url = entry.url,
                    title = entry.title,
                    isIncognito = activeTabId < 0L,
                )
            }
            openUrls.trySend(entry.url)
        }
    }

    // Mirrored synchronously so the field stays responsive; truncated so the per-keystroke scan
    // never sees a huge query.
    private fun onSearchQueryChange(query: String) {
        val bounded = query.take(BrowserLimits.MAX_HISTORY_QUERY_LENGTH)
        _uiState.update { state -> state.copy(searchQuery = bounded) }
        searchQueryFlow.value = bounded
    }

    private fun onLongPressEntry(entry: HistoryEntry) {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = entry) }
    }

    private fun onActionSheetDismiss() {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
    }

    /**
     * Always opens a normal tab, even from an incognito one. On a cap failure the existing tab-cap
     * message is shown and the screen stays open.
     */
    private fun onOpenInNewTab(entry: HistoryEntry) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
            val outcome = runCatching { tabs.createTab(entry.url) }
            val capReached = outcome.getOrNull().let { result ->
                result is Result.Error && result.throwable is MaxTabsReachedException
            }
            when {
                outcome.isFailure || capReached ->
                    snackbarEvents.tryEmit(HistoryErrorEvent.TabCapReached)
                else -> openUrls.trySend(entry.url)
            }
        }
    }

    /** Removes exactly the targeted row; other visits to the same URL stay. */
    private fun onDeleteEntry(entry: HistoryEntry) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
            runCatching { history.deleteById(entry.id) }
                .onFailure { cause ->
                    snackbarEvents.tryEmit(HistoryErrorEvent.DeletionFailed(cause))
                }
        }
    }

    private fun onCopyUrl(entry: HistoryEntry) {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
        if (clipboardWriter.copyUrl(entry.url)) {
            snackbarEvents.tryEmit(HistoryErrorEvent.ClipboardCopied)
        }
    }

    private fun onClearAllRequested() {
        _uiState.update { state -> state.copy(isClearAllDialogVisible = true) }
    }

    private fun onClearAllCancelled() {
        _uiState.update { state -> state.copy(isClearAllDialogVisible = false) }
    }

    private fun onClearAllConfirmed() {
        viewModelScope.launch {
            try {
                runCatching { clearHistory() }
                    .onFailure { cause ->
                        snackbarEvents.tryEmit(HistoryErrorEvent.DeletionFailed(cause))
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
