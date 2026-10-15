package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseIncognitoTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateIncognitoTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SwitchActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.util.TabsErrorEvent
import com.raumanian.thirtysix.browser.feature.tabs.domain.usecase.CloseAllIncognitoTabsUseCase
import com.raumanian.thirtysix.browser.feature.tabs.domain.usecase.CloseAllTabsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val createTab: CreateTabUseCase,
    private val createIncognitoTab: CreateIncognitoTabUseCase,
    private val switchActiveTab: SwitchActiveTabUseCase,
    private val closeTab: CloseTabUseCase,
    private val closeIncognitoTab: CloseIncognitoTabUseCase,
    private val closeAllTabs: CloseAllTabsUseCase,
    private val closeAllIncognitoTabs: CloseAllIncognitoTabsUseCase,
    @param:Named("default_home_url") private val homeUrl: String,
    private val faviconCache: FaviconCache,
    private val screenshotCache: ScreenshotCache,
) : ViewModel() {

    /** Bumps when a favicon is written, so cards re-read [faviconFor]. */
    val faviconVersion: kotlinx.coroutines.flow.StateFlow<Long> get() = faviconCache.version

    fun faviconFor(url: String): File? = faviconCache.fileFor(url)

    /** Bumps when a screenshot is written or deleted, so cards re-read [screenshotFor]. */
    val screenshotVersion: kotlinx.coroutines.flow.StateFlow<Long>
        get() = screenshotCache.version

    fun screenshotFor(tabId: Long): File? = screenshotCache.fileFor(tabId)

    private data class LocalState(
        val isCloseAllDialogVisible: Boolean = false,
        val isCloseAllIncognitoDialogVisible: Boolean = false,
        val errorEvent: TabsErrorEvent? = null,
        /** The chip the user picked; `null` until they pick one (then the active tab decides). */
        val modeOverride: TabsMode? = null,
    )

    private val localState: MutableStateFlow<LocalState> = MutableStateFlow(LocalState())

    private val _popBackEvent: MutableSharedFlow<Unit> =
        MutableSharedFlow(replay = 0, extraBufferCapacity = 1)

    /** One-shot signal that the Composable should pop the switcher route. */
    val popBackEvent = _popBackEvent.asSharedFlow()

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
            errorEvent = local.errorEvent,
            incognitoTabCount = incognitoCount,
            mode = mode,
            visibleTabs = tabs.filter { it.isIncognito == (mode == TabsMode.Incognito) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = TabsUiState.EMPTY,
    )

    fun onTabClick(tabId: Long) {
        viewModelScope.launch {
            if (tabId < 0L) {
                // SwitchActiveTabUseCase only knows normal tabs, so tapping an incognito
                // tab just closes the switcher without bumping its lastActiveAt.
                _popBackEvent.tryEmit(Unit)
            } else {
                switchActiveTab(tabId)
                _popBackEvent.tryEmit(Unit)
            }
        }
    }

    fun onCloseTab(tabId: Long) {
        viewModelScope.launch {
            if (tabId < 0L) {
                closeIncognitoTab(tabId)
            } else {
                closeTab(tabId)
            }
        }
    }

    fun onNewTabClick() {
        viewModelScope.launch {
            val result = createTab()
            if (result is Result.Error && result.throwable is MaxTabsReachedException) {
                localState.update { it.copy(errorEvent = TabsErrorEvent.MaxTabsReached) }
            } else if (result is Result.Success) {
                _popBackEvent.tryEmit(Unit)
            }
        }
    }

    fun onNewIncognitoTabClick() {
        viewModelScope.launch {
            val result = createIncognitoTab(homeUrl)
            when {
                result is Result.Error && result.throwable is MaxIncognitoTabsReachedException -> {
                    localState.update { it.copy(errorEvent = TabsErrorEvent.MaxIncognitoTabsReached) }
                }
                result is Result.Success -> _popBackEvent.tryEmit(Unit)
                else -> Unit
            }
        }
    }

    fun onCloseAllRequested() {
        localState.update { it.copy(isCloseAllDialogVisible = true) }
    }

    fun onCloseAllDismissed() {
        localState.update { it.copy(isCloseAllDialogVisible = false) }
    }

    fun onCloseAllConfirmed() {
        viewModelScope.launch {
            closeAllTabs()
            localState.update { it.copy(isCloseAllDialogVisible = false) }
            _popBackEvent.tryEmit(Unit)
        }
    }

    fun onCloseAllIncognitoRequested() {
        localState.update { it.copy(isCloseAllIncognitoDialogVisible = true) }
    }

    fun onCloseAllIncognitoDismissed() {
        localState.update { it.copy(isCloseAllIncognitoDialogVisible = false) }
    }

    fun onCloseAllIncognitoConfirmed() {
        viewModelScope.launch {
            closeAllIncognitoTabs()
            localState.update { it.copy(isCloseAllIncognitoDialogVisible = false) }
        }
    }

    /** A chip was tapped; create and close-all act on this kind of tab only. */
    fun onModeSelected(mode: TabsMode) {
        localState.update { it.copy(modeOverride = mode) }
    }

    fun consumeErrorEvent() {
        localState.update { it.copy(errorEvent = null) }
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
