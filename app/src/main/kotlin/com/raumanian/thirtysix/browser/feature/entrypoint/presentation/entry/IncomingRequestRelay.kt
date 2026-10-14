package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import androidx.annotation.StringRes
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/** One-slot hand-off from `MainActivity` to the UI; the latest request replaces an unconsumed older one. */
@Singleton
class IncomingRequestRelay @Inject constructor() {
    private val slot = MutableStateFlow<IncomingRequest?>(null)

    val pending: StateFlow<IncomingRequest?> = slot.asStateFlow()

    fun submit(request: IncomingRequest) {
        slot.value = request
    }

    fun restoreIfEmpty(request: IncomingRequest) {
        slot.update { current -> current ?: request }
    }

    /** Returns the waiting request and empties the slot, so it can be opened only once. */
    fun take(): IncomingRequest? = slot.getAndUpdate { null }
}

// A URL for the already-active tab when the tab cap forced a reuse. A channel, not a `SharedFlow`:
// on a cold start the browser screen may not be collecting yet.
@Singleton
class ActiveTabNavigationRelay @Inject constructor() {
    private val urls = Channel<String>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val requests: Flow<String> = urls.receiveAsFlow()

    fun send(url: String) {
        urls.trySend(url)
    }
}

/** One-shot messages about incoming content; a channel for the same cold-start reason as [ActiveTabNavigationRelay]. */
@Singleton
class EntryMessageRelay @Inject constructor() {
    private val ids = Channel<Int>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val messages: Flow<Int> = ids.receiveAsFlow()

    fun show(@StringRes message: Int) {
        ids.trySend(message)
    }
}

// Tabs opened for another app's link: Back with no history left closes the tab and returns to the
// sender. In memory only, so after process death the tab behaves like any other.
@Singleton
class ExternalTabRegistry @Inject constructor() {
    private val ids = MutableStateFlow<Set<Long>>(emptySet())

    val tabIds: StateFlow<Set<Long>> = ids.asStateFlow()

    fun add(tabId: Long) {
        ids.update { it + tabId }
    }

    fun remove(tabId: Long) {
        ids.update { it - tabId }
    }

    fun contains(tabId: Long): Boolean = tabId in ids.value

    fun removeAll(closedTabIds: Set<Long>) {
        if (closedTabIds.isNotEmpty()) ids.update { it - closedTabIds }
    }
}
