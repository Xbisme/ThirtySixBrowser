package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkResult
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the person does to the page on screen: star it, switch it to desktop mode, or open a new tab. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PageActionsViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
    private val pageStore: CurrentPageRepository,
    private val tabs: TabRepository,
    isUrlBookmarked: IsUrlBookmarkedUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PageActionsUiState())
    val uiState: StateFlow<PageActionsUiState> = _uiState.asStateFlow()

    // Kept until the screen collects them, as the consumed-once state fields they replace were.
    private val eventChannel = Channel<PageActionsEvent>(Channel.BUFFERED)
    val events: Flow<PageActionsEvent> = eventChannel.receiveAsFlow()

    // In memory only: new tabs and every tab after a process restart start in mobile mode. Tab ids
    // are never reused, so a closed tab's entry is inert.
    private val desktopModeTabs = MutableStateFlow<Set<Long>>(emptySet())

    val desktopMode: DesktopModeLookup = DesktopModeLookup { tabId -> tabId in desktopModeTabs.value }

    init {
        combine(pageStore.page.map { it.activeTab?.id }, desktopModeTabs) { id, desktop -> id != null && id in desktop }
            .distinctUntilChanged()
            .onEach { desktop -> _uiState.update { it.copy(isDesktopMode = desktop) } }
            .launchIn(viewModelScope)

        pageStore.page
            .map { it.url }
            .distinctUntilChanged()
            .flatMapLatest { url -> isUrlBookmarked(url) }
            .onEach { bookmarked -> _uiState.update { it.copy(isBookmarked = bookmarked) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: PageActionsAction) {
        when (action) {
            PageActionsAction.StarTapped -> onStarTapped()
            PageActionsAction.ToggleDesktopMode -> onDesktopModeToggled()
            PageActionsAction.LongPressNewTab -> onLongPressNewTab()
        }
    }

    private fun onStarTapped() {
        val page = pageStore.page.value
        viewModelScope.launch {
            val result = toggleBookmark(url = page.url, fallbackTitle = page.title)
            if (result is Result.Success) {
                val event = when (result.data) {
                    ToggleBookmarkResult.Added -> BookmarkSnackbarEvent.Added
                    ToggleBookmarkResult.Removed -> BookmarkSnackbarEvent.Removed
                }
                eventChannel.trySend(PageActionsEvent.Bookmark(event))
            }
        }
    }

    /** Flips the active tab's page mode; nothing without an active tab. The screen reloads the WebView. */
    private fun onDesktopModeToggled() {
        val tabId = pageStore.page.value.activeTab?.id ?: return
        desktopModeTabs.update { desktop -> if (tabId in desktop) desktop - tabId else desktop + tabId }
        eventChannel.trySend(PageActionsEvent.SetDesktopMode(tabId in desktopModeTabs.value))
    }

    /** Opens a new Home tab, or reports that the tab limit is reached. */
    private fun onLongPressNewTab() {
        viewModelScope.launch {
            val result = tabs.createTab(defaultHomeUrl)
            if (result is Result.Error && result.throwable is MaxTabsReachedException) {
                eventChannel.trySend(PageActionsEvent.TabLimit(TabsErrorEvent.MaxTabsReached))
            }
        }
    }
}
