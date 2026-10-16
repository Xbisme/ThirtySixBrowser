package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.domain.repository.TabSessionRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reads the shown tab's saved back and forward before its WebView is built, and saves it as the page moves. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TabSessionViewModel @Inject constructor(
    pageStore: CurrentPageRepository,
    private val sessions: TabSessionRepository,
    observeAllTabs: ObserveAllTabsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TabSessionUiState())
    val uiState: StateFlow<TabSessionUiState> = _uiState.asStateFlow()

    private class Shown(val tabId: Long, val state: ByteArray?)

    // The shown tab's latest state stays here, not in UI state: a byte array is no composable parameter.
    @Volatile
    private var shown: Shown? = null

    val savedState: SavedTabStateLookup = SavedTabStateLookup { tabId -> shown?.takeIf { it.tabId == tabId }?.state }

    // Kept until the screen collects them: a clear made from Settings or History reaches the page on return.
    private val eventChannel = Channel<TabSessionEvent>(Channel.BUFFERED)
    val events: Flow<TabSessionEvent> = eventChannel.receiveAsFlow()

    init {
        pageStore.page
            .map { it.activeTab?.id }
            .distinctUntilChanged()
            .filterNotNull()
            .mapLatest { tabId -> tabId to sessions.get(tabId) }
            .onEach { (tabId, session) ->
                shown = Shown(tabId, session?.state)
                _uiState.update { it.copy(loadedTabId = tabId) }
            }
            .launchIn(viewModelScope)

        sessions.clears
            .drop(1)
            .onEach {
                shown = shown?.let { Shown(it.tabId, null) }
                eventChannel.trySend(TabSessionEvent.ClearLiveHistory)
            }
            .launchIn(viewModelScope)

        // Closed tabs' sessions go too. An empty normal list is the moment before Home is seeded, never a sweep.
        observeAllTabs()
            .map { tabs -> tabs.mapTo(HashSet()) { it.id } }
            .filter { ids -> ids.any { it > 0L } }
            .distinctUntilChanged()
            .onEach { ids -> sessions.retainOnly(ids) }
            .launchIn(viewModelScope)
    }

    fun onAction(action: TabSessionAction) {
        when (action) {
            is TabSessionAction.Save -> save(action)
        }
    }

    private fun save(action: TabSessionAction.Save) {
        val session = TabSession(action.tabId, action.state, System.currentTimeMillis())
        // A renderer-loss replacement of the shown tab restores from the newest state.
        if (shown?.tabId == action.tabId) shown = Shown(action.tabId, action.state)
        viewModelScope.launch { sessions.save(session) }
    }
}
