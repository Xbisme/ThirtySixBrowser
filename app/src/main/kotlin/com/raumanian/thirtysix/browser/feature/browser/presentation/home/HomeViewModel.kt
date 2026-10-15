package com.raumanian.thirtysix.browser.feature.browser.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SwitchActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.browser.domain.model.GreetingPeriod
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ObserveHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The greeting is recomputed from [clock] in [onResume], never on a timer. Building shortcuts
 * checks cached files, so it runs on the default dispatcher, never on Main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // Three sources, two caches, a clock, one action, dispatchers.
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeActiveTab: ObserveActiveTabUseCase,
    observeHomeContent: ObserveHomeContentUseCase,
    observeUserSettings: ObserveUserSettingsUseCase,
    private val screenshotCache: ScreenshotCache,
    private val faviconCache: FaviconCache,
    private val clock: Clock,
    private val switchActiveTab: SwitchActiveTabUseCase,
    dispatchers: DispatcherProvider,
) : ViewModel() {

    private val greeting = MutableStateFlow(currentGreeting())

    val uiState: StateFlow<HomeUiState> = combine(
        observeActiveTab().map { it?.id }.distinctUntilChanged().flatMapLatest { observeHomeContent(it) },
        observeUserSettings().map { it.searchEngine }.distinctUntilChanged(),
        greeting,
        screenshotCache.version,
        faviconCache.version,
    ) { content, engine, period, _, _ ->
        HomeUiState(
            greeting = period,
            shortcuts = content.shortcuts.map { shortcut ->
                HomeShortcutUi(
                    shortcut = shortcut,
                    screenshot = shortcut.tabId?.let(screenshotCache::fileFor),
                    favicon = faviconCache.fileFor(shortcut.url),
                )
            },
            recent = content.recent,
            searchEngine = engine,
        )
    }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(greeting = greeting.value),
        )

    fun onResume() {
        greeting.value = currentGreeting()
    }

    /** Activates that tab; nothing is loaded. */
    fun onTabShortcut(tabId: Long) {
        viewModelScope.launch { switchActiveTab(tabId) }
    }

    private fun currentGreeting(): GreetingPeriod = GreetingPeriod.of(LocalTime.now(clock))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
