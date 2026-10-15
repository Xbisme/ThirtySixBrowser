package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.core.presentation.util.IncomingContentHooks
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingTarget
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.OpenOutcome
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase.OpenIncomingRequestUseCase
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase.ResolveIncomingRequestUseCase
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

sealed interface EntryEvent {
    /** Bring the browser to the front, popping whatever screen is above it */
    data object ShowBrowser : EntryEvent
}

/**
 * Opens what other apps send once onboarding is done; until then the request waits in the relay.
 * Created once, outside the `NavHost`: a second instance in a destination would have its events go unseen.
 */
@HiltViewModel
@Suppress("LongParameterList") // Hand-off pieces + the two use cases + tab store access; mirrors TabsViewModel.
class EntryPointViewModel @Inject constructor(
    private val relay: IncomingRequestRelay,
    settings: SettingsRepository,
    private val resolve: ResolveIncomingRequestUseCase,
    private val open: OpenIncomingRequestUseCase,
    private val externalTabs: ExternalTabRegistry,
    private val activeTabNavigation: ActiveTabNavigationRelay,
    private val messages: EntryMessageRelay,
    tabs: TabRepository,
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
        val onboardingDone = settings.observeSettings().map { it.isOnboardingCompleted }.distinctUntilChanged()
        combine(relay.pending, onboardingDone) { request, done -> done && request != null }
            .filter { ready -> ready }
            .onEach { handlePending() }
            .launchIn(viewModelScope)

        // However a tab closed, its id leaves the registry so a later tab cannot inherit "return to sender".
        // Only ids that disappear are dropped: the store may emit before a just-created tab is listed.
        var previousIds: Set<Long> = emptySet()
        tabs.observeTabs()
            .map { tabs -> tabs.mapTo(mutableSetOf()) { it.id } }
            .onEach { currentIds ->
                externalTabs.removeAll(previousIds - currentIds)
                previousIds = currentIds
            }
            .launchIn(viewModelScope)
    }

    /** Back on an untouched tab opened by another app closes it. */
    private fun leaveExternalTab(tabId: Long) {
        externalTabs.remove(tabId)
        viewModelScope.launch { closeTab(tabId) }
    }

    private suspend fun handlePending() {
        // Taken before anything else, so a resubscription cannot open it twice.
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
