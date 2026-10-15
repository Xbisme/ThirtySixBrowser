package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkException
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderUseCasesTest {

    @Test
    fun `CreateFolder rejects blank name`() = runTest {
        val repo = FakeBookmarkRepository()
        val result = CreateFolderUseCase(repo)("   ", null)
        assertTrue(result is Result.Error)
    }

    @Test
    fun `CreateFolder caps length to MAX_FOLDER_NAME_LENGTH`() = runTest {
        val repo = FakeBookmarkRepository()
        val longName = "x".repeat(500)
        val result = CreateFolderUseCase(repo)(longName, null)
        val id = (result as Result.Success).data
        val folder = repo.getFolder(id)!!
        assertTrue(folder.name.length <= 100)
    }

    @Test
    fun `RenameFolder updates the name`() = runTest {
        val repo = FakeBookmarkRepository()
        val id = (CreateFolderUseCase(repo)("Old", null) as Result.Success).data
        RenameFolderUseCase(repo)(id, "New")
        assertEquals("New", repo.getFolder(id)!!.name)
    }

    @Test
    fun `UpdateBookmark validates URL and trims title`() = runTest {
        val repo = FakeBookmarkRepository()
        val id = repo.seedBookmark(Bookmark(0, "old", "https://old.com", null, 1L, 1L))

        val result = UpdateBookmarkUseCase(repo)(
            bookmarkId = id,
            title = "  New title  ",
            url = "https://new.com",
            parentFolderId = null,
        )
        assertTrue(result is Result.Success)
        val updated = repo.getBookmark(id)!!
        assertEquals("New title", updated.title)
        assertEquals("https://new.com", updated.url)
    }

    @Test
    fun `UpdateBookmark rejects malformed URL`() = runTest {
        val repo = FakeBookmarkRepository()
        val id = repo.seedBookmark(Bookmark(0, "x", "https://x.com", null, 1L, 1L))

        val result = UpdateBookmarkUseCase(repo)(
            bookmarkId = id,
            title = "x",
            url = "ftp://bad.com",
            parentFolderId = null,
        )
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.UrlValidation)
    }
}
