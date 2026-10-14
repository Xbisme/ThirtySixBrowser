package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.domain.model.ShortcutKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 019 T054 — FR-013 / FR-014 build rule (data-model §3). */
class ObserveHomeContentUseCaseTest {

    private fun tab(id: Long, url: String, lastActive: Long, incognito: Boolean = false) =
        Tab(
            id = id,
            url = url,
            title = "Tab $id",
            position = id.toInt(),
            createdAt = 0L,
            lastActiveAt = lastActive,
            isIncognito = incognito,
        )

    private fun bookmark(url: String, createdAt: Long) =
        Bookmark(id = 0L, title = "Saved $url", url = url, parentFolderId = null, createdAt = createdAt, sortOrder = 0L)

    @Test
    fun order_isTabsThenSavedThenRecent_withoutTheActiveTab() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, "https://a.test/", 10), tab(2, "https://b.test/", 20)))
        val bookmarks = FakeBookmarkRepository().apply { seedBookmark(bookmark("https://c.test/", 5)) }
        val history = FakeHistoryRepository().apply { recordVisit("https://d.test/", "D", 1) }

        val content = ObserveHomeContentUseCase(tabs, bookmarks, history)(activeTabId = 1L).first()

        assertEquals(
            listOf(ShortcutKind.Tab, ShortcutKind.Saved, ShortcutKind.Recent),
            content.shortcuts.map { it.kind },
        )
        assertEquals(listOf("https://b.test/", "https://c.test/", "https://d.test/"), content.shortcuts.map { it.url })
        assertEquals(2L, content.shortcuts.first().tabId)
    }

    @Test
    fun duplicateAddresses_keepTheFirstKind_andHomeIsDropped() = runTest {
        val tabs = FakeTabRepository(
            initialTabs = listOf(
                tab(1, UrlConstants.HOME_PAGE_URL, 30),
                tab(2, "https://same.test/", 20),
                tab(3, "https://x.test/", 1),
            ),
        )
        val bookmarks = FakeBookmarkRepository().apply { seedBookmark(bookmark("https://same.test/", 5)) }
        val history = FakeHistoryRepository().apply { recordVisit("https://same.test/", "S", 1) }

        val content = ObserveHomeContentUseCase(tabs, bookmarks, history)(activeTabId = 3L).first()

        assertEquals(listOf("https://same.test/"), content.shortcuts.map { it.url })
        assertEquals(ShortcutKind.Tab, content.shortcuts.single().kind)
    }

    @Test
    fun incognitoTabs_neverAppear() = runTest {
        val tabs =
            FakeTabRepository(
                initialTabs = listOf(
                    tab(1, "https://open.test/", 1),
                    tab(-5, "https://secret.test/", 9, incognito = true),
                ),
            )

        val useCase = ObserveHomeContentUseCase(tabs, FakeBookmarkRepository(), FakeHistoryRepository())
        val content = useCase(activeTabId = null).first()

        assertTrue(content.shortcuts.none { it.url == "https://secret.test/" })
    }

    @Test
    fun limits_applyToShortcutsAndRecentRows() = runTest {
        val history = FakeHistoryRepository().apply {
            repeat(SEEDED) { recordVisit("https://h$it.test/", "H$it", it.toLong()) }
        }
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, "https://only.test/", 1)))

        val content = ObserveHomeContentUseCase(tabs, FakeBookmarkRepository(), history)(activeTabId = 1L).first()

        assertEquals(BrowserLimits.HOME_SHORTCUT_LIMIT, content.shortcuts.size)
        assertEquals(BrowserLimits.HOME_RECENT_LIMIT, content.recent.size)
        assertEquals("https://h${SEEDED - 1}.test/", content.recent.first().url)
    }

    @Test
    fun blankTitle_fallsBackToTheHost() = runTest {
        val history = FakeHistoryRepository().apply { recordVisit("https://untitled.test/page", "", 1) }
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, "https://active.test/", 1)))

        val content = ObserveHomeContentUseCase(tabs, FakeBookmarkRepository(), history)(activeTabId = 1L).first()

        assertEquals("untitled.test", content.shortcuts.single().title)
    }

    private companion object {
        const val SEEDED = 20
    }
}
