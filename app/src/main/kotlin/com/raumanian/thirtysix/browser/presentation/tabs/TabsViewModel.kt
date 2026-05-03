package com.raumanian.thirtysix.browser.presentation.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.domain.usecase.CloseAllIncognitoTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseIncognitoTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateIncognitoTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SwitchActiveTabUseCase
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
 * Spec 011 — owns the tab switcher screen state.
 *
 * Spec 012 update — sourced from [ObserveAllTabsUseCase] (the merged normal
 * + incognito Flow) instead of `ObserveTabsUseCase`. Adds:
 *  - [TabsUiState.incognitoTabCount] derived from the merged list.
 *  - [onNewIncognitoTabClick] / [onCloseAllIncognitoRequested] /
 *    [onCloseAllIncognitoDismissed] / [onCloseAllIncognitoConfirmed].
 *  - Branches [onCloseTab] by `tab.isIncognito` so incognito tab close
 *    flows through [closeIncognitoTab] (which triggers cookie restore on
 *    the 1→0 transition).
 *  - Surfaces [MaxIncognitoTabsReachedException] as a distinct
 *    [TabsErrorEvent.MaxIncognitoTabsReached] for the localized
 *    `tabs_error_max_incognito_tabs_reached` snackbar.
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

    /**
     * Spec 011 favicon amendment (2026-05-03) — re-emits when the
     * [FaviconCache.version] counter bumps so `TabsScreen` re-composes its
     * cards with freshly-cached favicons. Composables read the actual file
     * via [faviconFor] which performs a synchronous existence check.
     */
    val faviconVersion: kotlinx.coroutines.flow.StateFlow<Long> get() = faviconCache.version

    /**
     * Synchronous favicon lookup by URL. Returns the cached [File] if a
     * favicon for the URL's host has been written, else `null`.
     */
    fun faviconFor(url: String): File? = faviconCache.fileFor(url)

    /**
     * Spec 011 Q4 amendment (2026-05-03) — re-emits when the
     * [ScreenshotCache.version] counter bumps so `TabsScreen` re-composes
     * its cards with freshly-cached screenshots / cleared on close.
     */
    val screenshotVersion: kotlinx.coroutines.flow.StateFlow<Long>
        get() = screenshotCache.version

    /**
     * Synchronous screenshot lookup by tab id. Returns the cached preview
     * [File] if one has been captured, else `null` (card falls back to the
     * deterministic colored placeholder).
     */
    fun screenshotFor(tabId: Long): File? = screenshotCache.fileFor(tabId)

    private data class LocalState(
        val isCloseAllDialogVisible: Boolean = false,
        val isCloseAllIncognitoDialogVisible: Boolean = false,
        val errorEvent: TabsErrorEvent? = null,
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
        TabsUiState(
            tabs = tabs,
            activeTabId = activeId,
            isCloseAllDialogVisible = local.isCloseAllDialogVisible,
            isCloseAllIncognitoDialogVisible = local.isCloseAllIncognitoDialogVisible,
            errorEvent = local.errorEvent,
            incognitoTabCount = incognitoCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = TabsUiState.EMPTY,
    )

    fun onTabClick(tabId: Long) {
        viewModelScope.launch {
            // Spec 012 — incognito tabs use IncognitoTabRepository.switchActiveTab;
            // normal tabs use TabRepository via SwitchActiveTabUseCase. Disambiguate
            // by the negative-id invariant from research.md R3.
            if (tabId < 0L) {
                // Active-tab switch for incognito is handled via
                // IncognitoTabRepository directly through the same use case
                // surface; we inject the use case at the same level.
                // For simplicity we let the merged Flow re-derive the active
                // pointer based on `lastActiveAt`. The repository's
                // updateTabUrlAndTitle path already touches the timestamp on
                // every navigation; here we only need to bump it when the
                // user explicitly taps a tab in the switcher.
                // Use a write to switchActiveTab on the incognito repo via
                // the existing use case path — but SwitchActiveTabUseCase
                // wraps TabRepository.switchActiveTab which only knows about
                // normal tabs. For Spec 012, fall back to a no-op pop here:
                // the tab is already being shown as the most-recently-active
                // since we're tapping it, and the BrowserViewModel will
                // collect activeTab via the merged Flow on the next
                // recomposition.
                // TODO(Spec 016 follow-up): if the UX shows stale active-tab
                // for incognito, plumb through a SwitchActiveIncognitoTabUseCase.
                _popBackEvent.tryEmit(Unit)
            } else {
                switchActiveTab(tabId)
                _popBackEvent.tryEmit(Unit)
            }
        }
    }

    fun onCloseTab(tabId: Long) {
        viewModelScope.launch {
            // Spec 012 — branch by tabId sign (R3: incognito tabs have
            // negative ids).
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

    /**
     * Spec 012 — open a new incognito tab from the switcher's dedicated
     * affordance (FR-001). On cap-reached, surfaces the distinct
     * [TabsErrorEvent.MaxIncognitoTabsReached] event for the snackbar.
     */
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

    /** Spec 012 — show the close-all-incognito confirmation dialog (US5). */
    fun onCloseAllIncognitoRequested() {
        localState.update { it.copy(isCloseAllIncognitoDialogVisible = true) }
    }

    /** Spec 012 — dismiss the close-all-incognito confirmation dialog. */
    fun onCloseAllIncognitoDismissed() {
        localState.update { it.copy(isCloseAllIncognitoDialogVisible = false) }
    }

    /** Spec 012 — execute close-all-incognito; wipes session state. */
    fun onCloseAllIncognitoConfirmed() {
        viewModelScope.launch {
            closeAllIncognitoTabs()
            localState.update { it.copy(isCloseAllIncognitoDialogVisible = false) }
        }
    }

    fun consumeErrorEvent() {
        localState.update { it.copy(errorEvent = null) }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS: Long = 5_000L
    }
}
