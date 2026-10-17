package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.core.domain.util.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ObserveTabNeighboursUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SwitchToTabUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Swiping the address bar to the neighbouring tab, and the accessibility actions that do the same. */
@HiltViewModel
class TabSwipeViewModel @Inject constructor(
    observeTabNeighbours: ObserveTabNeighboursUseCase,
    settings: SettingsRepository,
    private val switchToTab: SwitchToTabUseCase,
    screenshotCache: ScreenshotCache,
) : ViewModel() {

    val uiState: StateFlow<TabSwipeUiState> = combine(
        observeTabNeighbours(),
        settings.observeSettings().map { it.isTabSwipeEnabled }.distinctUntilChanged(),
    ) { neighbours, enabled ->
        TabSwipeUiState(
            previous = neighbours.previous?.toPreview(),
            next = neighbours.next?.toPreview(),
            enabled = enabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TabSwipeUiState())

    /** Bumps when a page picture is saved or removed, so a remembered preview reads its file again. */
    val screenshotVersion: StateFlow<Long> = screenshotCache.version

    val screenshots: TabScreenshots = TabScreenshots { preview ->
        if (preview.isIncognito) null else screenshotCache.fileFor(preview.tabId)
    }

    private val _events = MutableSharedFlow<TabSwipeEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<TabSwipeEvent> = _events.asSharedFlow()

    fun onAction(action: TabSwipeAction) {
        when (action) {
            is TabSwipeAction.SwitchTo -> onSwitchTo(action)
        }
    }

    private fun onSwitchTo(action: TabSwipeAction.SwitchTo) {
        val state = uiState.value
        val preview = listOfNotNull(state.previous, state.next).firstOrNull { it.tabId == action.tabId }
        viewModelScope.launch {
            switchToTab(action.tabId)
            if (action.announce && preview != null) _events.tryEmit(TabSwipeEvent.Announce(preview))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** A normal tab's saved page picture; never one for an incognito tab. */
fun interface TabScreenshots {
    fun fileFor(preview: TabPreview): File?
}

private fun Tab.toPreview(): TabPreview {
    val isHome = url == UrlConstants.HOME_PAGE_URL
    return TabPreview(
        tabId = id,
        isIncognito = isIncognito,
        isHome = isHome,
        host = if (isHome || isIncognito) "" else url.extractHostnameOrSelf(),
        title = if (isIncognito) "" else title,
    )
}
