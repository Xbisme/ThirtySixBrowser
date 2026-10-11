package com.raumanian.thirtysix.browser.presentation.entry

import androidx.annotation.StringRes
import com.raumanian.thirtysix.browser.domain.model.IncomingRequest
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

/**
 * Spec 020 data-model §4 — the one-slot hand-off from `MainActivity` to the UI.
 *
 * A newer request replaces an unconsumed older one (the person's latest tap wins). It is
 * process-scoped, so it survives a rotation while onboarding holds it (FR-019); the activity
 * also saves an unconsumed request so it survives process death (research R3).
 */
@Singleton
class IncomingRequestRelay @Inject constructor() {
    private val slot = MutableStateFlow<IncomingRequest?>(null)

    /** The request waiting to be opened, or `null`. */
    val pending: StateFlow<IncomingRequest?> = slot.asStateFlow()

    fun submit(request: IncomingRequest) {
        slot.value = request
    }

    /** Puts back a request saved before process death, only into an empty slot (research R3). */
    fun restoreIfEmpty(request: IncomingRequest) {
        slot.update { current -> current ?: request }
    }

    /** Returns the waiting request and empties the slot, so it can be opened only once (INV-3). */
    fun take(): IncomingRequest? = slot.getAndUpdate { null }
}

/**
 * Spec 020 data-model §5 — a URL to load into the tab that is already active, used when the
 * tab cap made the app reuse a tab instead of creating one (research R6).
 *
 * A one-slot channel rather than a `SharedFlow`: on a cold start the browser screen may not be
 * collecting yet when the URL is sent, and a channel keeps it until it is received. One
 * consumer (`BrowserScreen`).
 */
@Singleton
class ActiveTabNavigationRelay @Inject constructor() {
    private val urls = Channel<String>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val requests: Flow<String> = urls.receiveAsFlow()

    fun send(url: String) {
        urls.trySend(url)
    }
}

/**
 * Spec 020 data-model §5a — one-shot messages about incoming content (a refused link, the tab
 * limit). Shown by the browser's snackbar host so only one view model instance is involved
 * (research R5). A one-slot channel for the same cold-start reason as [ActiveTabNavigationRelay].
 */
@Singleton
class EntryMessageRelay @Inject constructor() {
    private val ids = Channel<Int>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val messages: Flow<Int> = ids.receiveAsFlow()

    fun show(@StringRes message: Int) {
        ids.trySend(message)
    }
}

/**
 * Spec 020 data-model §6 — ids of tabs opened for a link from another app. Back on such a tab,
 * with no page history left, closes it and returns to the sending app (FR-006). In memory only:
 * after process death the tab simply behaves like any other.
 */
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

    /** Drops ids of tabs that were closed, whatever closed them (data-model §6). */
    fun removeAll(closedTabIds: Set<Long>) {
        if (closedTabIds.isNotEmpty()) ids.update { it - closedTabIds }
    }
}
