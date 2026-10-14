package com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestionSnapshot
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.FilterAddressSuggestionsUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.LoadAddressSuggestionsSourceUseCase
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
 * One snapshot of bookmarks and history is loaded when editing starts and dropped when it ends;
 * keystrokes filter it in memory off the main thread. Nothing is written and nothing reaches the
 * network, so typing in incognito leaves no trace.
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

    fun onEditingStarted() {
        if (snapshot.value != null || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            snapshot.value = withContext(dispatchers.default) { loadSource() }
        }
    }

    /** A field still showing the page's own address unchanged suggests nothing. */
    fun onTextChanged(text: String, currentPageUrl: String?) {
        input.value = Input(text, currentPageUrl)
    }

    fun onFocusChanged(focused: Boolean) = if (focused) onEditingStarted() else onEditingEnded()

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
