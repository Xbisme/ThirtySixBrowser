package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.model.Tab

/**
 * Spec 019 FR-013 — one shortcut card on the Home page (data-model §3). Derived from data the
 * app already stores; nothing about a shortcut is persisted.
 *
 * @property stableKey lazy-list key: `tab:<id>`, `bookmark:<id>` or `history:<id>`.
 * @property tabId set only for [ShortcutKind.Tab] — tapping activates that tab instead of loading.
 */
data class HomeShortcut(
    val stableKey: String,
    val kind: ShortcutKind,
    val title: String,
    val url: String,
    val host: String,
    val tabId: Long? = null,
)

/** Which kind of shortcut a card is, shown as its tag and used as its lazy `contentType`. */
enum class ShortcutKind { Tab, Saved, Recent }

/** Spec 019 FR-013 / FR-014 — everything the Home page lists. */
data class HomeContent(
    val shortcuts: List<HomeShortcut>,
    val recent: List<HistoryEntry>,
) {
    companion object {
        val EMPTY = HomeContent(shortcuts = emptyList(), recent = emptyList())
    }
}
