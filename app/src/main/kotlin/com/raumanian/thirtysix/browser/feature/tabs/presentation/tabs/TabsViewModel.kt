package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import com.raumanian.thirtysix.browser.feature.tabs.domain.usecase.CloseAllTabsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tab switcher state over the merged normal + incognito tab list. Incognito tabs have negative
 * ids, so close and switch branch on the id's sign.
 */
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class TabsViewModel @Inject constructor(
    observeAllTabs: ObserveAllTabsUseCase,
    private val tabs: TabRepository,
    private val incognitoTabs: IncognitoTabRepository,
    private val closeTab: CloseTabUseCase,
    private val closeAllTabs: CloseAllTabsUseCase,
    @param:Named("default_home_url") private val homeUrl: String,
    faviconCache: FaviconCache,
    screenshotCache: ScreenshotCache,
) : ViewModel() {

    val images: TabImages = CachedTabImages(faviconCache, screenshotCache)

    /** Bumps when a favicon is written, so cards read [images] again. */
    val faviconVersion: StateFlow<Long> = faviconCache.version

    /** Bumps when a screenshot is written or deleted, so cards read [images] again. */
    val screenshotVersion: StateFlow<Long> = screenshotCache.version

    private data class LocalState(
        val isCloseAllDialogVisible: Boolean = false,
        val isCloseAllIncognitoDialogVisible: Boolean = false,
        /** The chip the user picked; `null` until they pick one (then the active tab decides). */
        val modeOverride: TabsMode? = null,
    )

    private val localState: MutableStateFlow<LocalState> = MutableStateFlow(LocalState())

    private val showBrowserEvents: MutableSharedFlow<TabsEvent> =
        MutableSharedFlow(replay = 0, extraBufferCapacity = 1)

    // Kept until the screen collects it, as the consumed-once state field it replaces was.
    private val errors = Channel<TabsErrorEvent>(Channel.BUFFERED)

    val events: Flow<TabsEvent> = merge(
        showBrowserEvents.asSharedFlow(),
        errors.receiveAsFlow().map { TabsEvent.Error(it) },
    )

    val uiState: StateFlow<TabsUiState> = combine(
        observeAllTabs().map { tabs ->
            val activeId = tabs.maxByOrNull(Tab::lastActiveAt)?.id
            val incognitoCount = tabs.count(Tab::isIncognito)
            Triple(tabs, activeId, incognitoCount)
        },
        localState,
    ) { (tabs, activeId, incognitoCount), local ->
        val mode = resolveMode(tabs, activeId, incognitoCount, local.modeOverride)
        TabsUiState(
            tabs = tabs,
            activeTabId = activeId,
            isCloseAllDialogVisible = local.isCloseAllDialogVisible,
            isCloseAllIncognitoDialogVisible = local.isCloseAllIncognitoDialogVisible,
            incognitoTabCount = incognitoCount,
            mode = mode,
            visibleTabs = tabs.filter { it.isIncognito == (mode == TabsMode.Incognito) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = TabsUiState.EMPTY,
    )

    fun onAction(action: TabsAction) {
        when (action) {
            is TabsAction.TabClick -> onTabClick(action.tabId)
            is TabsAction.CloseTab -> onCloseTab(action.tabId)
            TabsAction.NewTabClick -> onNewTabClick()
            TabsAction.NewIncognitoTabClick -> onNewIncognitoTabClick()
            TabsAction.CloseAllRequested -> showCloseAll(visible = true)
            TabsAction.CloseAllDismissed -> showCloseAll(visible = false)
            TabsAction.CloseAllConfirmed -> onCloseAllConfirmed()
            TabsAction.CloseAllIncognitoRequested -> showCloseAllIncognito(visible = true)
            TabsAction.CloseAllIncognitoDismissed -> showCloseAllIncognito(visible = false)
            TabsAction.CloseAllIncognitoConfirmed -> onCloseAllIncognitoConfirmed()
            // Create and close-all then act on this kind of tab only.
            is TabsAction.ModeSelected -> localState.update { it.copy(modeOverride = action.mode) }
        }
    }

    private fun onTabClick(tabId: Long) {
        viewModelScope.launch {
            if (tabId < 0L) {
                // The tab repository only knows normal tabs, so tapping an incognito
                // tab just closes the switcher without bumping its lastActiveAt.
                showBrowserEvents.tryEmit(TabsEvent.ShowBrowser)
            } else {
                tabs.switchActiveTab(tabId)
                showBrowserEvents.tryEmit(TabsEvent.ShowBrowser)
            }
        }
    }

    private fun onCloseTab(tabId: Long) {
        viewModelScope.launch {
            if (tabId < 0L) {
                incognitoTabs.closeTab(tabId)
            } else {
                closeTab(tabId)
            }
        }
    }

    private fun onNewTabClick() {
        viewModelScope.launch {
            val result = tabs.createTab(homeUrl)
            if (result is Result.Error && result.throwable is MaxTabsReachedException) {
                errors.trySend(TabsErrorEvent.MaxTabsReached)
            } else if (result is Result.Success) {
                showBrowserEvents.tryEmit(TabsEvent.ShowBrowser)
            }
        }
    }

    private fun onNewIncognitoTabClick() {
        viewModelScope.launch {
            val result = incognitoTabs.createTab(homeUrl)
            when {
                result is Result.Error && result.throwable is MaxIncognitoTabsReachedException -> {
                    errors.trySend(TabsErrorEvent.MaxIncognitoTabsReached)
                }
                result is Result.Success -> showBrowserEvents.tryEmit(TabsEvent.ShowBrowser)
                else -> Unit
            }
        }
    }

    private fun showCloseAll(visible: Boolean) {
        localState.update { it.copy(isCloseAllDialogVisible = visible) }
    }

    private fun showCloseAllIncognito(visible: Boolean) {
        localState.update { it.copy(isCloseAllIncognitoDialogVisible = visible) }
    }

    private fun onCloseAllConfirmed() {
        viewModelScope.launch {
            closeAllTabs()
            localState.update { it.copy(isCloseAllDialogVisible = false) }
            showBrowserEvents.tryEmit(TabsEvent.ShowBrowser)
        }
    }

    private fun onCloseAllIncognitoConfirmed() {
        viewModelScope.launch {
            incognitoTabs.closeAll()
            localState.update { it.copy(isCloseAllIncognitoDialogVisible = false) }
        }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS: Long = 5_000L

        fun resolveMode(tabs: List<Tab>, activeId: Long?, incognitoCount: Int, override: TabsMode?): TabsMode =
            when {
                incognitoCount == 0 -> TabsMode.Open
                override != null -> override
                tabs.firstOrNull { it.id == activeId }?.isIncognito == true -> TabsMode.Incognito
                else -> TabsMode.Open
            }
    }
}
