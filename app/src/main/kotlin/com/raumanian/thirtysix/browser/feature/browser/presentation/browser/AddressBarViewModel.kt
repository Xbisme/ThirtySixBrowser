package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.AddressBarSubmitResult
import com.raumanian.thirtysix.browser.core.domain.model.classifyAddressBarInput
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the person types and where it loads: the address bar, Home's search field and Back to Home. */
@HiltViewModel
class AddressBarViewModel @Inject constructor(
    private val pageStore: CurrentPageRepository,
    private val searchEngine: SearchEngineRepository,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddressBarUiState())
    val uiState: StateFlow<AddressBarUiState> = _uiState.asStateFlow()

    // Kept until the screen collects them: a load must never be lost.
    private val eventChannel = Channel<AddressBarEvent>(Channel.BUFFERED)
    val events: Flow<AddressBarEvent> = eventChannel.receiveAsFlow()

    fun onAction(action: AddressBarAction) {
        when (action) {
            is AddressBarAction.TextChange -> _uiState.update { it.copy(text = action.text) }
            is AddressBarAction.FocusChange -> _uiState.update { it.copy(isFocused = action.focused) }
            AddressBarAction.Submit -> submit(_uiState.value.text)
            AddressBarAction.Clear -> _uiState.update { it.copy(text = "") }
            AddressBarAction.HomePressed -> onHomePressed()
            AddressBarAction.HomeOverlayDismissed -> _uiState.update { it.copy(homeOverlay = false) }
            is AddressBarAction.HomeSubmit -> if (submit(action.input)) onNavigateFromHome()
            AddressBarAction.NavigateFromHome -> onNavigateFromHome()
            AddressBarAction.BackWithoutHistory -> onBackWithoutHistory()
        }
    }

    /** Shows the Home page over a loaded page; does nothing on Home. */
    private fun onHomePressed() {
        if (pageStore.page.value.url != UrlConstants.HOME_PAGE_URL) _uiState.update { it.copy(homeOverlay = true) }
    }

    private fun onNavigateFromHome() {
        _uiState.update { it.copy(homeOverlay = false, cameFromHome = true) }
    }

    /** Back with no WebView history: returns the tab to Home when the page was opened from Home in a normal tab. */
    private fun onBackWithoutHistory() {
        val page = pageStore.page.value
        if (!_uiState.value.cameFromHome || page.isIncognito) return
        pageStore.setTitle("")
        pageStore.setUrl(UrlConstants.HOME_PAGE_URL)
        _uiState.update { it.copy(cameFromHome = false, homeOverlay = false) }
        val activeId = page.activeTab?.id ?: return
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(
                tabId = activeId,
                url = UrlConstants.HOME_PAGE_URL,
                title = "",
                isIncognito = false,
            )
        }
    }

    // True when something was submitted; a search URL is then built and loaded asynchronously.
    private fun submit(raw: String): Boolean =
        when (val classified = classifyAddressBarInput(raw)) {
            AddressBarSubmitResult.Empty -> false
            is AddressBarSubmitResult.Url -> {
                eventChannel.trySend(AddressBarEvent.LoadUrl(classified.target))
                true
            }
            is AddressBarSubmitResult.Query -> {
                viewModelScope.launch {
                    eventChannel.trySend(AddressBarEvent.LoadUrl(searchEngine.buildSearchUrl(classified.text)))
                }
                true
            }
        }
}
