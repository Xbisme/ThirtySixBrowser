package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The page's pending requests, oldest first; only [head] is shown, so one dialog at a time.
 * Owned by the browser screen; main thread only.
 */
class PageRequestQueue {
    private val items = ArrayDeque<PageRequest>()
    private val _head = MutableStateFlow<PageRequest?>(null)

    val head: StateFlow<PageRequest?> = _head.asStateFlow()

    /**
     * Adds [request]. A permission request identical to one already waiting (same tab, page, site
     * and capabilities) joins it and receives the same answer instead of showing a second dialog.
     */
    fun enqueue(request: PageRequest) {
        if (request is PageRequest.Permission) {
            val same = items.filterIsInstance<PageRequest.Permission>().firstOrNull { it.isSameAs(request) }
            if (same != null) {
                same.absorb(request)
                return
            }
        }
        items.addLast(request)
        publish()
    }

    fun complete(request: PageRequest) {
        items.remove(request)
        publish()
    }

    /** Removes, without answering, the waiting requests the page itself withdrew. */
    fun withdraw(match: (PageRequest.Permission) -> Boolean) {
        val gone = items.filterIsInstance<PageRequest.Permission>().filter(match)
        gone.forEach { it.withdraw() }
        items.removeAll(gone.toSet())
        publish()
    }

    /** Cancels every request that does not belong to [tabId]'s page [generation]. */
    fun dropStale(tabId: Long?, generation: Long) {
        val stale = items.filter { it.tabId != tabId || it.generation != generation }
        stale.forEach { it.cancel() }
        items.removeAll(stale.toSet())
        publish()
    }

    /** The browser screen is going away: nothing pending may stay unanswered. */
    fun dispose() {
        items.forEach { it.cancel() }
        items.clear()
        publish()
    }

    private fun publish() {
        _head.value = items.firstOrNull()
    }
}
