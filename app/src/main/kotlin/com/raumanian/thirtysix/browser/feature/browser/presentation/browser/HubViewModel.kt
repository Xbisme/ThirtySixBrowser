package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateIncognitoTabUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ObserveHasActiveDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Apart from [BrowserViewModel] on purpose: only the open hub subscribes to [hasActiveDownloads],
 * so the platform download service is polled only while the hub is on screen.
 */
@HiltViewModel
class HubViewModel @Inject constructor(
    observeHasActiveDownloads: ObserveHasActiveDownloadsUseCase,
    private val createIncognitoTab: CreateIncognitoTabUseCase,
    @param:Named("default_home_url") private val homeUrl: String,
) : ViewModel() {

    val hasActiveDownloads: StateFlow<Boolean> = observeHasActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private val _events = MutableSharedFlow<HubEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    /** "Browse without a trace": opens a new incognito tab, within the incognito tab limit. */
    fun onNewIncognitoTab() {
        viewModelScope.launch {
            val result = createIncognitoTab(homeUrl)
            if (result is Result.Error && result.throwable is MaxIncognitoTabsReachedException) {
                _events.tryEmit(HubEvent.MaxIncognitoTabsReached)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 1_000L
    }
}

sealed interface HubEvent {
    /** The separate incognito tab limit was hit. */
    data object MaxIncognitoTabsReached : HubEvent
}
