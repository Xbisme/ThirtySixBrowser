package com.raumanian.thirtysix.browser.presentation.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.model.IncomingTarget
import com.raumanian.thirtysix.browser.domain.model.OpenOutcome
import com.raumanian.thirtysix.browser.domain.usecase.OpenIncomingRequestUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ResolveIncomingRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Spec 020 — one-shot navigation signal from [EntryPointViewModel]. */
sealed interface EntryEvent {
    /** Bring the browser to the front, popping whatever screen is above it (FR-005). */
    data object ShowBrowser : EntryEvent
}

/**
 * Spec 020 research R5 / contracts C2 — opens what other apps send, once onboarding is done.
 *
 * Created ONCE, outside the `NavHost` in `AppNavGraph`, so it lives as long as the activity; no
 * destination may create a second instance (its events would go unseen). Messages for the
 * person go to [EntryMessageRelay], which the browser's snackbar host shows.
 *
 * While onboarding (Spec 018) is incomplete the request simply waits in the relay (FR-019); the
 * view model never writes the onboarding flag.
 */
@HiltViewModel
@Suppress("LongParameterList") // Hand-off pieces + the two use cases + tab store access; mirrors TabsViewModel.
class EntryPointViewModel @Inject constructor(
    private val relay: IncomingRequestRelay,
    observeUserSettings: ObserveUserSettingsUseCase,
    private val resolve: ResolveIncomingRequestUseCase,
    private val open: OpenIncomingRequestUseCase,
    private val externalTabs: ExternalTabRegistry,
    private val activeTabNavigation: ActiveTabNavigationRelay,
    private val messages: EntryMessageRelay,
    observeTabs: ObserveTabsUseCase,
    private val closeTab: CloseTabUseCase,
) : ViewModel() {

    // A one-slot channel: the graph may not be collecting yet when a cold-start request is
    // handled, and the signal must not be lost.
    private val eventChannel = Channel<EntryEvent>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: Flow<EntryEvent> = eventChannel.receiveAsFlow()

    /** Handed to `BrowserScreen` by `AppNavGraph`. */
    val browserHooks = IncomingContentHooks(
        loadInActiveTab = activeTabNavigation.requests,
        messages = messages.messages,
        externalTabIds = externalTabs.tabIds,
        onLeaveExternalTab = ::leaveExternalTab,
    )

    init {
        val onboardingDone = observeUserSettings().map { it.isOnboardingCompleted }.distinctUntilChanged()
        combine(relay.pending, onboardingDone) { request, done -> done && request != null }
            .filter { ready -> ready }
            .onEach { handlePending() }
            .launchIn(viewModelScope)

        // Data-model §6 — whatever closed a tab (Back, close button, swipe, close all), its id
        // leaves the registry, so a later tab can never inherit the "return to sender" Back.
        // Only ids that were in an earlier list and are now gone are dropped: the tab store may
        // emit a list that does not yet include a tab just created and marked.
        var previousIds: Set<Long> = emptySet()
        observeTabs()
            .map { tabs -> tabs.mapTo(mutableSetOf()) { it.id } }
            .onEach { currentIds ->
                externalTabs.removeAll(previousIds - currentIds)
                previousIds = currentIds
            }
            .launchIn(viewModelScope)
    }

    /** FR-006 — Back on an untouched tab opened by another app closes it. */
    private fun leaveExternalTab(tabId: Long) {
        externalTabs.remove(tabId)
        viewModelScope.launch { closeTab(tabId) }
    }

    private suspend fun handlePending() {
        // Taken before anything else, so a resubscription cannot open it twice (INV-3).
        val request = relay.take() ?: return
        when (val target = resolve(request)) {
            IncomingTarget.Rejected -> messages.show(R.string.entry_link_rejected)
            IncomingTarget.Nothing -> Unit
            is IncomingTarget.Load, is IncomingTarget.Search -> applyOutcome(open(target))
        }
        eventChannel.trySend(EntryEvent.ShowBrowser)
    }

    private fun applyOutcome(outcome: OpenOutcome) {
        when (outcome) {
            is OpenOutcome.OpenedInNewTab -> externalTabs.add(outcome.tabId)
            is OpenOutcome.OpenedInExistingTab -> {
                messages.show(R.string.entry_tab_limit_reused)
                if (outcome.loadInPlace) activeTabNavigation.send(outcome.url)
            }
            OpenOutcome.Duplicate, OpenOutcome.Failed -> Unit
        }
    }
}
