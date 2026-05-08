package com.raumanian.thirtysix.browser.domain.usecase

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBookmarksByFolderUseCaseTest {

    @Test
    fun `combines folders and bookmarks for a given parent`() = runTest {
        val repo = FakeBookmarkRepository()
        repo.seedFolder(BookmarkFolder(0, "A", null, 1L))
        repo.seedFolder(BookmarkFolder(0, "B", null, 2L))
        repo.seedBookmark(Bookmark(0, "Root bookmark", "https://r.com", null, 100L, 100L))
        val useCase = ObserveBookmarksByFolderUseCase(repo)

        useCase(null).test {
            val first = awaitItem()
            assertEquals(2, first.folders.size)
            assertEquals(1, first.bookmarks.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `empty folder emits empty lists`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = ObserveBookmarksByFolderUseCase(repo)
        useCase(folderId = 999L).test {
            val first = awaitItem()
            assertEquals(0, first.folders.size)
            assertEquals(0, first.bookmarks.size)
            cancelAndConsumeRemainingEvents()
        }
    }
}
