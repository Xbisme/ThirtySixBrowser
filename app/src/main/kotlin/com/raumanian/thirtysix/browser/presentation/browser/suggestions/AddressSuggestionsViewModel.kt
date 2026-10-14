package com.raumanian.thirtysix.browser.presentation.browser.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.AddressSuggestionSnapshot
import com.raumanian.thirtysix.browser.domain.usecase.FilterAddressSuggestionsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.LoadAddressSuggestionsSourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spec 022 US2 — address suggestions while the person types, on the browser's address bar and on
 * the Home page's search field (research R5, R6).
 *
 * When editing starts, one snapshot of bookmarks and history is loaded off the main thread. Each
 * keystroke is then filtered in memory, also off the main thread, and a newer keystroke cancels
 * an older one (`mapLatest`). When editing ends the snapshot is dropped, so it costs memory only
 * while the person types.
 *
 * Nothing here writes anything, anywhere, and nothing reaches the network (FR-015, FR-022, I-8,
 * I-9). That is why typing in an incognito tab leaves no trace.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AddressSuggestionsViewModel @Inject constructor(
    private val loadSource: LoadAddressSuggestionsSourceUseCase,
    private val filter: FilterAddressSuggestionsUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private data class Input(val text: String, val currentPageUrl: String?)

    private val snapshot = MutableStateFlow<AddressSuggestionSnapshot?>(null)
    private val input = MutableStateFlow(Input(text = "", currentPageUrl = null))
    private var loadJob: Job? = null

    val uiState: StateFlow<AddressSuggestionsUiState> = combine(snapshot, input) { source, typed -> source to typed }
        .mapLatest { (source, typed) -> suggestionsFor(source, typed) }
        .flowOn(dispatchers.default)
        .map(::AddressSuggestionsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AddressSuggestionsUiState(),
        )

    /** An address entry gained focus: load this session's snapshot once. */
    fun onEditingStarted() {
        if (snapshot.value != null || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            snapshot.value = withContext(dispatchers.default) { loadSource() }
        }
    }

    /**
     * The entry's text changed. [currentPageUrl] is the page's own address: a field still showing
     * it unchanged suggests nothing (FR-021).
     */
    fun onTextChanged(text: String, currentPageUrl: String?) {
        input.value = Input(text, currentPageUrl)
    }

    /** An address entry gained ([focused]) or lost focus: starts or ends a suggestion session. */
    fun onFocusChanged(focused: Boolean) = if (focused) onEditingStarted() else onEditingEnded()

    /** Editing ended (submit, a suggestion chosen, focus lost): drop everything. */
    fun onEditingEnded() {
        loadJob?.cancel()
        loadJob = null
        snapshot.value = null
        input.value = Input(text = "", currentPageUrl = null)
    }

    private fun suggestionsFor(source: AddressSuggestionSnapshot?, typed: Input) = when {
        source == null || typed.text.isBlank() || typed.text == typed.currentPageUrl -> emptyList()
        else -> filter(source, typed.text, BrowserLimits.MAX_ADDRESS_SUGGESTIONS)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
