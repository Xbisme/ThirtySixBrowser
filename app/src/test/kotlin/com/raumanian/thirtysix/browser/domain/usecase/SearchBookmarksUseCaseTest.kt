package com.raumanian.thirtysix.browser.domain.usecase

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
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
}
