package com.raumanian.thirtysix.browser.feature.browser.domain.model

import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.model.Tab

/**
 * One shortcut card on the Home page, derived from data the app already stores; nothing about a
 * Shortcut is persisted.
 */
data class HomeShortcut(
    /** Lazy-list key: `tab:<id>`, `bookmark:<id>` or `history:<id>`. */
    val stableKey: String,
    val kind: ShortcutKind,
    val title: String,
    val url: String,
    val host: String,
    /** Set only for [ShortcutKind.Tab] — tapping activates that tab instead of loading. */
    val tabId: Long? = null,
)

/** Which kind of shortcut a card is, shown as its tag and used as its lazy `contentType`. */
enum class ShortcutKind { Tab, Saved, Recent }

/** Everything the Home page lists. */
data class HomeContent(
    val shortcuts: List<HomeShortcut>,
    val recent: List<HistoryEntry>,
) {
    companion object {
        val EMPTY = HomeContent(shortcuts = emptyList(), recent = emptyList())
    }
}
