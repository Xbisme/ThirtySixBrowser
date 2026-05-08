package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteBookmarkUseCaseTest {

    @Test
    fun `deletes existing bookmark`() = runTest {
        val repo = FakeBookmarkRepository()
        val id = repo.seedBookmark(Bookmark(0, "X", "https://x.com", null, 1L, 1L))
        val useCase = DeleteBookmarkUseCase(repo)

        val result = useCase(id)
        assertTrue(result is Result.Success)
        assertEquals(null, repo.getBookmark(id))
    }

    @Test
    fun `missing id returns BookmarkNotFound`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = DeleteBookmarkUseCase(repo)
        val result = useCase(999L)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.BookmarkNotFound)
    }
}
