package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/** The find bar; it closes when a new page loads or another tab comes forward, since its highlights are gone. */
@HiltViewModel
class FindInPageViewModel @Inject constructor(pageStore: CurrentPageRepository) : ViewModel() {
    private val _state = MutableStateFlow(FindInPageState())

    // Kept apart from the page state: a result per keystroke recomposes only the find bar.
    val state: StateFlow<FindInPageState> = _state.asStateFlow()

    init {
        pageStore.page
            .map { it.generation }
            .distinctUntilChanged()
            .drop(1)
            .onEach { _state.value = FindInPageState() }
            .launchIn(viewModelScope)
    }

    fun onAction(action: FindInPageAction) {
        when (action) {
            FindInPageAction.Opened -> _state.value = FindInPageState(isOpen = true)
            is FindInPageAction.QueryChanged -> onQueryChanged(action.query)
            FindInPageAction.Closed -> _state.value = FindInPageState()
            is FindInPageAction.Result -> onResult(action.activeOrdinal, action.total)
        }
    }

    private fun onQueryChanged(query: String) {
        _state.update { state ->
            if (!state.isOpen) {
                state
            } else {
                state.copy(query = query.take(BrowserLimits.MAX_FIND_QUERY_LENGTH), current = 0, total = 0)
            }
        }
    }

    // A late result after the bar closed, or while it is empty, must not reopen or relabel it.
    private fun onResult(activeOrdinal: Int, total: Int) {
        _state.update { state ->
            when {
                !state.isOpen || state.query.isEmpty() -> state
                total <= 0 -> state.copy(current = 0, total = 0)
                else -> state.copy(current = (activeOrdinal + 1).coerceIn(1, total), total = total)
            }
        }
    }
}
