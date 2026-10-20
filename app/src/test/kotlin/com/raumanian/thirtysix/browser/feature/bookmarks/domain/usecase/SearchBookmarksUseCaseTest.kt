package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchBookmarksUseCaseTest {

    @Test
    fun `case-insensitive substring matches title and url`() = runTest {
        val repo = FakeBookmarkRepository()
        repo.seedBookmark(Bookmark(0, "Kotlin Lang", "https://kotlin.org", null, 1L, 1L))
        repo.seedBookmark(Bookmark(0, "Other", "https://example.com", null, 2L, 2L))
        val useCase = SearchBookmarksUseCase(repo)

        useCase("KOTLIN").test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals("Kotlin Lang", first[0].bookmark.title)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `result row carries folder path for non-root bookmarks`() = runTest {
        val repo = FakeBookmarkRepository()
        val folderId = (repo.createFolder("Work", null) as Result.Success).data
        repo.seedBookmark(Bookmark(0, "Doc", "https://doc.com", folderId, 1L, 1L))
        val useCase = SearchBookmarksUseCase(repo)

        useCase("doc").test {
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals(listOf("Work"), first[0].folderPath.map { it.name })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `root-level bookmark has empty folder path`() = runTest {
        val repo = FakeBookmarkRepository()
        repo.seedBookmark(Bookmark(0, "Root", "https://r.com", null, 1L, 1L))
        val useCase = SearchBookmarksUseCase(repo)

        useCase("root").test {
            val first = awaitItem()
            assertEquals(emptyList<Any>(), first[0].folderPath)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `folder paths are built without reading ancestor chains`() = runTest {
        val repo = CountingBookmarkRepository()
        val parent = repo.seedFolder(BookmarkFolder(0, "Work", null, 1L))
        val child = repo.seedFolder(BookmarkFolder(0, "Docs", parent, 1L))
        repo.seedBookmark(Bookmark(0, "Doc", "https://doc.com", child, 1L, 1L))

        SearchBookmarksUseCase(repo)("doc").test {
            assertEquals(listOf("Work", "Docs"), awaitItem().single().folderPath.map { it.name })
            cancelAndConsumeRemainingEvents()
        }
        assertEquals(0, repo.ancestorReads)
    }

    @Test
    fun `a folder cycle ends the path instead of looping`() = runTest {
        val repo = FakeBookmarkRepository()
        repo.seedFolder(BookmarkFolder(CYCLE_A, "A", CYCLE_B, 1L))
        repo.seedFolder(BookmarkFolder(CYCLE_B, "B", CYCLE_A, 1L))
        repo.seedBookmark(Bookmark(0, "Doc", "https://doc.com", CYCLE_A, 1L, 1L))

        SearchBookmarksUseCase(repo)("doc").test {
            assertEquals(listOf("B", "A"), awaitItem().single().folderPath.map { it.name })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `results are capped at the search limit`() = runTest {
        val repo = FakeBookmarkRepository()
        repeat(BrowserLimits.BOOKMARK_SEARCH_MAX_RESULTS + 10) {
            repo.seedBookmark(Bookmark(0, "Doc $it", "https://doc$it.com", null, it.toLong(), 1L))
        }

        SearchBookmarksUseCase(repo)("doc").test {
            assertEquals(BrowserLimits.BOOKMARK_SEARCH_MAX_RESULTS, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    private class CountingBookmarkRepository : FakeBookmarkRepository() {
        var ancestorReads = 0

        override suspend fun getAncestorChain(folderId: Long): List<BookmarkFolder> {
            ancestorReads++
            return super.getAncestorChain(folderId)
        }
    }

    private companion object {
        const val CYCLE_A = 10L
        const val CYCLE_B = 11L
    }
}
