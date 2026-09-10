package com.raumanian.thirtysix.browser.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.extensions.historyDayBucketOf
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter
import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.domain.usecase.ClearAllHistoryUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteHistoryEntryUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveHistoryEntriesUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
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

/**
 * Spec 014 — view model for the HistoryScreen.
 *
 * Surface built up across user stories:
 *  - **US1** — observe the persisted list, derive day-bucketed groups, dispatch
 *    tap-to-replace-active-tab via [UpdateActiveTabUrlAndTitleUseCase] (mirrors
 *    Spec 013 `BookmarksViewModel.onBookmarkTap`).
 *  - **US2** — live substring search over title-or-URL, gated at
 *    [BrowserLimits.SEARCH_MIN_CHARS] with no debounce (FR-011a / Q4).
 *  - **US3** — long-press action sheet: open in a new normal tab, delete a single
 *    row, copy the URL (FR-018 … FR-022).
 *  - **US4** — confirmed clear-all (FR-023 … FR-026).
 *
 * Coordinates use cases from two domains: history domain (Spec 014) + tabs domain
 * (Spec 011's [UpdateActiveTabUrlAndTitleUseCase] / [CreateTabUseCase]).
 * ViewModel-level use-case coordination — Constitution §IV compliant.
 */
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

    /**
     * Spec 014 — one-shot snackbar channel. `replay = 0` so a re-subscribing Composable
     * never re-shows a stale message; `DROP_OLDEST` with a 1-slot buffer so a burst of
     * rapid actions surfaces the most recent outcome instead of suspending the emitter.
     * Mirrors Spec 013's `bookmarkSnackbarEvent` channel policy.
     */
    private val _historySnackbarEvent: MutableSharedFlow<HistoryErrorEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val historySnackbarEvent: SharedFlow<HistoryErrorEvent> = _historySnackbarEvent.asSharedFlow()

    /** FR-011 — drives the filter half of the listing pipeline. */
    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")

    /** FR-010 — captured active tab id used by [onEntryTap]. */
    private var activeTabId: Long = 0L

    /** FR-010a — captured incognito flag used by [onEntryTap]. */
    private var lastKnownIsIncognito: Boolean = false

    init {
        // Listing pipeline: entries × query → filtered → bucketed. Recomputes on every
        // repository emission AND every keystroke, so FR-017 (a new matching entry shows
        // up under an active query) falls out of the combine for free.
        combine(observeHistoryEntries(), searchQueryFlow) { entries, query -> entries to query }
            .map { (entries, query) -> Derived(entries, groupByDay(filterEntries(entries, query))) }
            // Filtering + day-bucketing is O(n) over the whole history, which at the
            // SC-005 benchmark size (10 000 rows) is far too much work for the UI thread —
            // `viewModelScope` dispatches on `Dispatchers.Main.immediate`, so before this
            // `flowOn` every emission *and every keystroke* blocked rendering (measured on
            // an API 36 emulator: p99 frame 450 ms on open, 200 ms while typing, 70 % janky).
            // `flowOn` applies to the upstream only, so the derivation moves to the default
            // dispatcher while the `onEach` state write below stays on the main thread.
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

    // ──────── US1 — Open entry (FR-010 / FR-010a) ────────

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

    // ──────── US2 — Search (FR-011 … FR-017) ────────

    /**
     * FR-011a — the raw query is mirrored into state synchronously so the text field
     * stays responsive, and pushed onto [searchQueryFlow] to re-drive the filter.
     * Input longer than [BrowserLimits.MAX_HISTORY_QUERY_LENGTH] is truncated so the
     * per-keystroke substring scan can never see a pathological query.
     */
    fun onSearchQueryChange(query: String) {
        val bounded = query.take(BrowserLimits.MAX_HISTORY_QUERY_LENGTH)
        _uiState.update { state -> state.copy(searchQuery = bounded) }
        searchQueryFlow.value = bounded
    }

    /** FR-016 — clearing the field restores the full grouped list. */
    fun onSearchQueryClear() {
        onSearchQueryChange("")
    }

    // ──────── US3 — Per-entry actions (FR-018 … FR-022) ────────

    fun onLongPressEntry(entry: HistoryEntry) {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = entry) }
    }

    /** FR-022 — tap-outside / system-back closes the sheet without acting. */
    fun onActionSheetDismiss() {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
    }

    /**
     * FR-019 / FR-019a / Q2 — always creates a **normal** tab, regardless of whether the
     * currently-active tab is incognito. The new tab becomes active (see
     * [CreateTabUseCase]), so the screen is dismissed via the [HistoryUiState.openUrl]
     * signal on success. A cap-reached failure surfaces the existing Spec 011 message
     * and leaves the screen open.
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

    /** FR-020 — removes exactly the targeted row; sibling rows for the same URL stay. */
    fun onDeleteEntry(entry: HistoryEntry) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
            runCatching { deleteHistoryEntry(entry.id) }
                .onFailure { cause ->
                    _historySnackbarEvent.tryEmit(HistoryErrorEvent.DeletionFailed(cause))
                }
        }
    }

    /** FR-021 — copies the URL verbatim and confirms via snackbar. */
    fun onCopyUrl(entry: HistoryEntry) {
        _uiState.update { state -> state.copy(pendingActionSheetTarget = null) }
        if (clipboardWriter.copyUrl(entry.url)) {
            _historySnackbarEvent.tryEmit(HistoryErrorEvent.ClipboardCopied)
        }
    }

    // ──────── US4 — Clear all (FR-023 … FR-026) ────────

    fun onClearAllRequested() {
        _uiState.update { state -> state.copy(isClearAllDialogVisible = true) }
    }

    /** FR-026 — Cancel / tap-outside / system-back leaves history untouched. */
    fun onClearAllCancelled() {
        _uiState.update { state -> state.copy(isClearAllDialogVisible = false) }
    }

    /** FR-025 — wipes every entry; the observer drives the screen to its empty state. */
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

    // ──────── Derivation helpers ────────

    /** Result of one derivation pass, computed off the main thread. */
    private data class Derived(
        val entries: List<HistoryEntry>,
        val groups: List<HistoryUiState.DayGroup>,
    )

    /**
     * FR-011a / FR-012 / FR-013 — below the threshold the list passes through untouched.
     * At or above it, matches are case-insensitive substrings of the title **or** the URL.
     * `String.contains` treats every character literally, so SQL-ish input (`%`, `_`, `'`)
     * carries no special meaning (FR-013).
     */
    private fun filterEntries(entries: List<HistoryEntry>, query: String): List<HistoryEntry> {
        if (query.length < BrowserLimits.SEARCH_MIN_CHARS) return entries
        return entries.filter { entry ->
            entry.title.contains(query, ignoreCase = true) ||
                entry.url.contains(query, ignoreCase = true)
        }
    }

    /**
     * FR-008 / FR-014 — buckets the (already reverse-chronological) list by local day.
     * Bucketing preserves input order within each group, and a group is emitted only
     * when it has at least one entry — so zero-match days disappear under an active query.
     */
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
