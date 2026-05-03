package com.raumanian.thirtysix.browser.presentation.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.domain.usecase.CloseAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SwitchActiveTabUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
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
 * State derivation:
 *  - [uiState.tabs] / [uiState.activeTabId] come from [ObserveTabsUseCase]
 *    (Flow-driven; auto-seeded on empty per FR-019).
 *  - [uiState.isCloseAllDialogVisible] / [uiState.errorEvent] come from an
 *    internal [MutableStateFlow] for transient UI-only state.
 *
 * NavController coupling lives at the Composable layer via [popBackEvent] —
 * a one-shot SharedFlow emitted by [onTabClick], [onCloseAllConfirmed], and
 * [onNewTabClick] on success (M3 remediation — replaces an earlier
 * `LaunchedEffect(activeTabId)` heuristic that fired on initial composition).
 */
@HiltViewModel
@Suppress("LongParameterList")
class TabsViewModel @Inject constructor(
    observeTabs: ObserveTabsUseCase,
    private val createTab: CreateTabUseCase,
    private val switchActiveTab: SwitchActiveTabUseCase,
    private val closeTab: CloseTabUseCase,
    private val closeAllTabs: CloseAllTabsUseCase,
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
        val errorEvent: TabsErrorEvent? = null,
    )

    private val localState: MutableStateFlow<LocalState> = MutableStateFlow(LocalState())

    private val _popBackEvent: MutableSharedFlow<Unit> =
        MutableSharedFlow(replay = 0, extraBufferCapacity = 1)

    /** One-shot signal that the Composable should pop the switcher route. */
    val popBackEvent = _popBackEvent.asSharedFlow()

    val uiState: StateFlow<TabsUiState> = combine(
        observeTabs().map { tabs -> tabs to tabs.maxByOrNull(Tab::lastActiveAt)?.id },
        localState,
    ) { (tabs, activeId), local ->
        TabsUiState(
            tabs = tabs,
            activeTabId = activeId,
            isCloseAllDialogVisible = local.isCloseAllDialogVisible,
            errorEvent = local.errorEvent,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = TabsUiState.EMPTY,
    )

    fun onTabClick(tabId: Long) {
        viewModelScope.launch {
            switchActiveTab(tabId)
            _popBackEvent.tryEmit(Unit)
        }
    }

    fun onCloseTab(tabId: Long) {
        viewModelScope.launch { closeTab(tabId) }
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

    fun consumeErrorEvent() {
        localState.update { it.copy(errorEvent = null) }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS: Long = 5_000L
    }
}
