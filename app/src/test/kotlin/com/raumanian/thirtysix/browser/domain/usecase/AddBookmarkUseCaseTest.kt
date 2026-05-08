package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.error.BookmarkUrlValidationError
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddBookmarkUseCaseTest {

    @Test
    fun `valid url and title produces Success with new id`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = AddBookmarkUseCase(repo)

        val result = useCase(url = "https://example.com", title = "Example", parentFolderId = null)
        assertTrue(result is Result.Success)
        val id = (result as Result.Success).data
        assertTrue(id > 0L)
        val bookmark = repo.getBookmark(id)!!
        assertEquals("https://example.com", bookmark.url)
        assertEquals("Example", bookmark.title)
    }

    @Test
    fun `bare host gets https-prefixed before save`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = AddBookmarkUseCase(repo)

        val result = useCase(url = "example.com", title = "X", parentFolderId = null)
        val id = (result as Result.Success).data
        assertEquals("https://example.com", repo.getBookmark(id)!!.url)
    }

    @Test
    fun `blank title falls back to url string per FR-007`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = AddBookmarkUseCase(repo)

        val result = useCase(url = "https://example.com", title = "   ", parentFolderId = null)
        val id = (result as Result.Success).data
        assertEquals("https://example.com", repo.getBookmark(id)!!.title)
    }

    @Test
    fun `invalid url is rejected with UrlValidation error`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = AddBookmarkUseCase(repo)

        val result = useCase(url = "ftp://example.com", title = "X", parentFolderId = null)
        assertTrue(result is Result.Error)
        val error = (result as Result.Error).throwable
        assertTrue(error is BookmarkException.UrlValidation)
        assertEquals(
            BookmarkUrlValidationError.InvalidScheme,
            (error as BookmarkException.UrlValidation).reason,
        )
    }

    @Test
    fun `empty url is rejected as Empty validation`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = AddBookmarkUseCase(repo)

        val result = useCase(url = "  ", title = "X", parentFolderId = null)
        assertTrue(result is Result.Error)
    }
}
