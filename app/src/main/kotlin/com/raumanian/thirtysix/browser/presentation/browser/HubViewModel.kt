package com.raumanian.thirtysix.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.domain.usecase.CreateIncognitoTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveHasActiveDownloadsUseCase
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
 * Spec 019 — what the hub does beyond the browser's own state.
 *
 * Kept apart from [BrowserViewModel] on purpose (analysis U2): [hasActiveDownloads] is shared
 * `WhileSubscribed`, and only the open hub subscribes, so the platform download service is
 * polled only while the hub is on screen. The incognito banner's action lives here too, so the
 * browser's view model gains no new dependency.
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

    /** One-shot outcomes for the browser's snackbar (replay 0, newest wins). */
    val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    /** "Browse without a trace" — opens a new incognito tab (Spec 012 cap applies). */
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

/** Spec 019 — one-shot results of hub actions. */
sealed interface HubEvent {
    /** Spec 012 FR-021 — the separate 50-tab incognito cap was hit. */
    data object MaxIncognitoTabsReached : HubEvent
}
