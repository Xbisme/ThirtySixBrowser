package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import com.raumanian.thirtysix.browser.feature.browser.domain.model.HomeContent
import com.raumanian.thirtysix.browser.feature.browser.domain.model.HomeShortcut
import com.raumanian.thirtysix.browser.feature.browser.domain.model.ShortcutKind
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Home's shortcuts: open normal tabs except [activeTabId], then recent bookmarks, then recent
 * visits; each address once, never the Home address. Recent rows are the newest history entries.
 * Incognito never appears: its tabs live in a repository this never reads, and are not recorded.
 */
class ObserveHomeContentUseCase @Inject constructor(
    private val tabRepository: TabRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
) {
    operator fun invoke(activeTabId: Long?): Flow<HomeContent> = combine(
        tabRepository.observeTabs(),
        bookmarkRepository.observeRecentBookmarks(BrowserLimits.HOME_SHORTCUT_LIMIT),
        historyRepository.observeRecent(HISTORY_FETCH),
    ) { tabs, bookmarks, history ->
        val candidates = buildList {
            tabs.filter { it.id != activeTabId && !it.isIncognito }
                .sortedByDescending { it.lastActiveAt }
                .forEach { add(HomeShortcut("tab:${it.id}", ShortcutKind.Tab, it.title, it.url, it.url.host(), it.id)) }
            bookmarks.forEach {
                add(HomeShortcut("bookmark:${it.id}", ShortcutKind.Saved, it.title, it.url, it.url.host()))
            }
            history.forEach {
                add(HomeShortcut("history:${it.id}", ShortcutKind.Recent, it.title, it.url, it.url.host()))
            }
        }
        val seen = HashSet<String>()
        val shortcuts = candidates
            .filter { it.url != UrlConstants.HOME_PAGE_URL && it.url.isNotBlank() && seen.add(it.url) }
            .map { if (it.title.isBlank()) it.copy(title = it.host) else it }
            .take(BrowserLimits.HOME_SHORTCUT_LIMIT)
        HomeContent(shortcuts = shortcuts, recent = history.take(BrowserLimits.HOME_RECENT_LIMIT))
    }

    /** Same result as the address bar's `extractHostnameOrSelf`, without Android types. */
    private fun String.host(): String =
        runCatching { URI(this).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: this

    private companion object {
        const val HISTORY_FETCH = BrowserLimits.HOME_SHORTCUT_LIMIT + BrowserLimits.HOME_RECENT_LIMIT
    }
}
