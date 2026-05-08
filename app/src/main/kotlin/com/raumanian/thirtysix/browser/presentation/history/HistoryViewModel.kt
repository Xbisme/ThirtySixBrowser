package com.raumanian.thirtysix.browser.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.extensions.historyDayBucketOf
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveHistoryEntriesUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Spec 014 — view model for the HistoryScreen.
 *
 * US1 surface (this file): observe the persisted history list, derive day-bucketed
 * groups, dispatch tap-to-replace-active-tab via [UpdateActiveTabUrlAndTitleUseCase]
 * (mirrors Spec 013 `BookmarksViewModel.onBookmarkTap`).
 *
 * US2 (search), US3 (long-press actions), and US4 (clear-all) extend this VM with
 * additional flows + use case injections in their respective phases.
 *
 * Coordinates use cases from two domains: history domain (Spec 014) + tabs domain
 * (Spec 011's [UpdateActiveTabUrlAndTitleUseCase] for the "open entry in active tab"
 * path per FR-010). ViewModel-level use-case coordination — Constitution §IV compliant.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistoryEntries: ObserveHistoryEntriesUseCase,
    observeActiveTab: ObserveActiveTabUseCase,
    observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    val faviconCache: FaviconCache,
) : ViewModel() {

    private val _uiState: MutableStateFlow<HistoryUiState> = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** FR-010 — captured active tab id used by [onEntryTap]. */
    private var activeTabId: Long = 0L

    /** FR-010a — captured incognito flag used by [onEntryTap]. */
    private var lastKnownIsIncognito: Boolean = false

    init {
        // Drive entries + groupedEntries from the repository observer. Bucketing
        // recomputes per emission against the device's current local date.
        observeHistoryEntries()
            .onEach { entries ->
                val grouped = groupByDay(entries)
                _uiState.update { state ->
                    state.copy(entries = entries, groupedEntries = grouped)
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
